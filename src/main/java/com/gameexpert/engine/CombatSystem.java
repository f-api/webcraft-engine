package com.gameexpert.engine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import com.gameexpert.engine.inventory.PlayerInventory;
import com.gameexpert.engine.mob.Mob;
import com.gameexpert.engine.mob.MobType;
import com.gameexpert.engine.mob.ZombiePigman;
import com.gameexpert.ws.dto.WsMessages;

/**
 * 플레이어 → 몹 근접 전투(월드당 1개, 틱 스레드 전용). 액션 드레인 단계(①)에서 호출됩니다.
 *
 * 검증: 대상 몹이 살아 있고, 눈 기준 시선 레이가 몹 AABB 와 3.0블록 이내에서 교차하며,
 * 공격 간격과 무관하게 성립한다. 다만 마지막 공격 이후 충전 시간과 선택 무기의 attack speed 에 따라
 * MC 미완충 배율(0.2~1.0)이 피해에 적용된다. 첫 공격은 완충 상태다.
 * 피해는 MC 20포인트 스케일({@link CombatRules})이며, 명중 시 {@code mobHurt} 브로드캐스트,
 * 사망 시 사유 "death" 를 기록해 몹 틱(③.5) 뒤 {@code mobDespawn} 으로 나가게 합니다.
 * 명중 시 선택 슬롯의 검/도구 내구도가 1 깎이며, 이 경우 개인 인벤토리 갱신이 필요함을 반환합니다.
 */
final class CombatSystem {

    /**
     * [PIGLIN-LOOTING] 사망 정산 콜백. 바닐라 {@code EnchantmentHelper.getMobLooting} 은
     * <b>치명타를 넣은 그 순간의 주손 무기</b>에서 약탈 레벨을 읽으므로, 드랍표가 무기를 알아야 한다.
     */
    @FunctionalInterface
    interface MobDeathSink {
        /** @param lootingLevel 처치 무기의 약탈 레벨(비플레이어 사인은 0) */
        void accept(Mob mob, String reason, int lootingLevel);
    }

    private final List<Mob> mobs;                    // MobRuntime.mobs() 라이브 참조
    private final MobDeathSink onDeath;              // (mob, reason, looting) — 디스폰 사유 + 사망 드랍
    private final Consumer<Object> broadcast;        // WS 메시지 송출
    private final BiConsumer<String, Long> onPlayerHitMob;
    /**
     * [GUARDIAN] 가시 반사 피해 배출구. (닉네임, 피해량, 가시 몹) 를 받아 런타임이 플레이어
     * 피해로 해석한다. 배선되지 않은 순수 테스트는 아무 일도 하지 않는다.
     */
    private final ThornsSink onThorns;
    private final Map<String, Long> lastAttackTick = new HashMap<>();
    /** [ENCHANT-WIDE] 마지막으로 받아들인 창 찌르기 틱(찌르기 충전 판정). */
    private final Map<String, Long> lastSpearStabTick = new HashMap<>();
    private java.util.Random enchantmentRandom = new java.util.Random();

    /** [GUARDIAN] 가시 반사 한 건. */
    @FunctionalInterface
    interface ThornsSink {
        void accept(String nickname, int damage, Mob source);
    }

    CombatSystem(List<Mob> mobs, BiConsumer<Mob, String> onDeath, Consumer<Object> broadcast) {
        this(mobs, onDeath, broadcast, (nickname, mobId) -> {});
    }

    CombatSystem(List<Mob> mobs, BiConsumer<Mob, String> onDeath, Consumer<Object> broadcast,
            BiConsumer<String, Long> onPlayerHitMob) {
        this(mobs, (mob, reason, looting) -> onDeath.accept(mob, reason), broadcast, onPlayerHitMob);
    }

    CombatSystem(List<Mob> mobs, MobDeathSink onDeath, Consumer<Object> broadcast,
            BiConsumer<String, Long> onPlayerHitMob) {
        this(mobs, onDeath, broadcast, onPlayerHitMob, (nickname, damage, source) -> {});
    }

    CombatSystem(List<Mob> mobs, MobDeathSink onDeath, Consumer<Object> broadcast,
            BiConsumer<String, Long> onPlayerHitMob, ThornsSink onThorns) {
        this.mobs = mobs;
        this.onDeath = onDeath;
        this.broadcast = broadcast;
        this.onPlayerHitMob = onPlayerHitMob;
        this.onThorns = onThorns;
    }

    /**
     * attack 액션 처리: 검증 통과 시 피해를 주고 연출/사망을 알립니다.
     * @return 명중으로 검/도구 내구가 깎여 개인 인벤토리 갱신이 필요하면 true, 거부/무마모면 false.
     */
    boolean handleAttack(PlayerTickState player, long mobId, long tickNo) {
        return handleAttack(player, mobId, tickNo, false);
    }

    boolean handleAttack(PlayerTickState player, long mobId, long tickNo, boolean sprinting) {
        AttackResult result = handleAttackResult(player, mobId, tickNo, sprinting);
        emitHurt(result);
        return result.inventoryChanged();
    }

    /**
     * [SULFUR-SOUNDS] 몹 사운드 배출구(MobSystem.mobSound). 몸통 블록을 삼킨 유황 큐브의 넉백은 한 번마다
     * 아키타입 {@code hit_sound} 를 낸다(SulfurCube.knockback 끝의 playSound). 순수 테스트는 아무 일도 하지 않는다.
     */
    private BiConsumer<Mob, String> mobSound = (mob, kind) -> {};

    void setMobSoundSink(BiConsumer<Mob, String> sink) {
        mobSound = sink == null ? (mob, kind) -> {} : sink;
    }

    /**
     * [SPEAR-KINETIC] 플레이어 위치 worldSound 배출구(MobSystem.worldSound). 창 쓰기·찌르기·돌진 인챈트 소리는
     * 바닐라 {@code Level.playSound(entity, entity.getX/Y/Z, …, 1, 1)} 라 공격자 발 위치에서 난다.
     */
    private BiConsumer<PlayerTickState, String> playerSound = (player, kind) -> {};

    void setPlayerSoundSink(BiConsumer<PlayerTickState, String> sink) {
        playerSound = sink == null ? (player, kind) -> {} : sink;
    }

    /** [EC-MOBS] 아이템 액자 피격 처리(MobSystem.hurtItemFrame). */
    private BiConsumer<com.gameexpert.engine.mob.ItemFrame, String> itemFrameHurt = (frame, nickname) -> {};

    void setItemFrameHurt(BiConsumer<com.gameexpert.engine.mob.ItemFrame, String> sink) {
        itemFrameHurt = sink == null ? (frame, nickname) -> {} : sink;
    }

    /** [DRAGON] 엔드 수정·드래곤 근접 진입점({@link DragonFightSystem}). */
    interface DragonCombat {
        void hurtCrystal(Mob crystal, String attackerNickname);

        com.gameexpert.engine.dragon.DragonBrain.HurtResult meleeDragon(PlayerTickState player,
                com.gameexpert.engine.mob.EnderDragon dragon, double amount);

        /**
         * [SPEAR-KINETIC] 창 돌진이 부위를 찔렀다: 바닐라 {@code KineticWeapon.damageEntities} 는 부위를 부모 드래곤으로
         * 바꿔 {@code stabAttack(dragon)} 하고, {@code EnderDragon.hurtServer} 는 몸통 부위 피해다.
         */
        default com.gameexpert.engine.dragon.DragonBrain.HurtResult kineticDragon(PlayerTickState player,
                com.gameexpert.engine.mob.EnderDragon dragon, double amount) {
            return new com.gameexpert.engine.dragon.DragonBrain.HurtResult(false, false, false, false);
        }
    }

    private DragonCombat dragonCombat;

    void setDragonCombat(DragonCombat combat) {
        dragonCombat = combat;
    }

    /**
     * [CONTAINER-MENUS] {@code Player#attack} damage against a non-mob entity (placed armor stand or
     * minecart): the charged melee damage (effects before the charge scale) and the falling
     * critical x1.5. The attack cooldown advances exactly as a mob hit does.
     */
    double chargedDamageAgainstEntity(PlayerTickState player, long tickNo) {
        Long last = lastAttackTick.get(player.nickname());
        lastAttackTick.put(player.nickname(), tickNo);
        var inv = player.inventory();
        short weapon = inv.itemType(inv.selectedSlot());
        long elapsed = last == null ? 0L : Math.max(0L, tickNo - last);
        double damage = CombatRules.effectiveChargedMeleeDamage(weapon, elapsed, last == null,
                player.statusEffects().meleeDamagePenalty());
        if (player.airborne() && player.fallPeakY() > player.y()
                && !SpearRules.suppressesCritical(weapon)) {
            damage *= 1.5;
        }
        return damage;
    }

    AttackResult handleAttackResult(PlayerTickState player, long mobId, long tickNo, boolean sprinting) {
        if (player == null || player.isDead()) return AttackResult.rejected();
        Mob mob = findAlive(mobId);
        if (mob == null) return AttackResult.rejected();               // 없거나 이미 죽음
        if (!CombatRules.withinAuthorityReach(player.x(), player.y(), player.z(), player.crouching(),
                mob.x, mob.y, mob.z, mob.width(), mob.height())) {
            return AttackResult.rejected();                            // 서버 허용 사거리 밖 → 거부
        }
        // [EC-MOBS] 아이템 액자는 HangingEntity.skipAttackInteraction: 플레이어 공격이 곧 hurtServer 이고 휘두르기
        // 결과(내구·허기·휩쓸기·명중 이펙트)는 없다. 넣은 아이템을 떨구거나 액자째 부서진다.
        if (mob instanceof com.gameexpert.engine.mob.ItemFrame frame) {
            itemFrameHurt.accept(frame, player.nickname());
            return AttackResult.rejected();
        }
        // [DRAGON] 엔드 수정: Player.attack → EndCrystal.hurtServer(살아 있지 않아 내구·넉백·휩쓸기가 없다).
        if (mob.type == MobType.END_CRYSTAL) {
            if (dragonCombat != null) dragonCombat.hurtCrystal(mob, player.nickname());
            return AttackResult.rejected();
        }
        Long last = lastAttackTick.get(player.nickname());
        lastAttackTick.put(player.nickname(), tickNo);
        var inv = player.inventory();
        short weapon = inv.itemType(inv.selectedSlot());
        long elapsed = last == null ? 0L : Math.max(0L, tickNo - last);
        boolean firstAttack = last == null;
        // [ENCHANT-WIDE] 인챈트 보너스(날카로움·강타·살충·찌르기)는 기본 피해와 따로 계산한다 — 26.3
        // Player.attack 은 치명타 ×1.5 를 기본 피해에만 곱하고 공격 세기에 비례한 보너스를 그 뒤에 더한다.
        com.gameexpert.engine.enchant.WideEnchantments weaponEnchantments =
                inv.wideEnchantments(inv.selectedSlot());
        // 나약·힘은 바닐라대로 ATTACK_DAMAGE 속성 자리라 충전 배율과 치명타 배율보다 먼저 적용된다.
        double dmg = CombatRules.effectiveChargedMeleeDamage(weapon, elapsed, firstAttack,
                player.statusEffects().meleeDamagePenalty());
        // [MACE] 낙하 강타: 바닐라 Player.attack 은 충전 배율을 곱한 기본 피해에
        // Item.getAttackDamageBonus(= MaceItem 강타 보너스)를 더한 뒤 그 합에 치명타 ×1.5 를 건다.
        double fallDistance = player.currentFallDistance();
        boolean smash = MaceRules.canSmashAttack(weapon, fallDistance, player.gliding());
        if (smash) {
            dmg += MaceRules.smashBonusDamage(fallDistance, heldLevel(weapon, weaponEnchantments,
                    com.gameexpert.engine.enchant.EnchantmentRules.DENSITY));
        }
        // [MACE] 파괴: 무기의 armor_effectiveness 가산치(음수)가 대상 방어 효율을 깎는다.
        double armorEffectivenessDelta = MaceRules.breachArmorEffectivenessDelta(heldLevel(weapon,
                weaponEnchantments, com.gameexpert.engine.enchant.EnchantmentRules.BREACH));
        // [ENCHANT-WIDE] 휩쓸기 피해의 기준은 치명타 배율 전 기본 피해다(휩쓸기는 치명타와 함께 나지 않는다).
        final double sweepAttackDamage = dmg;
        // 서버 낙하 추적기가 공중이며 실제로 정점 아래로 내려온 상태만 치명타로 확정한다.
        // 달리기 공격은 기존 sprint knockback 결과이므로 치명타와 동시에 성립하지 않는다.
        // [SPEAR][B] 창은 **크리티컬이 아예 나지 않는다**(핀 §5 원문: 잽은 "cannot do critical
        // hits or sprint-knockback attacks"). 그래서 낙하 조건이 성립해도 ×1.5 를 곱하지 않고
        // 파티클 조건도 성립시키지 않는다 — 조건 자체를 여기서 끈다.
        boolean critical = player.airborne() && player.fallPeakY() > player.y() && !sprinting
                && !SpearRules.suppressesCritical(weapon);
        if (critical) dmg *= 1.5;
        double enchantmentBonus = CombatRules.enchantmentMeleeBonus(
                weapon, weaponEnchantments, mob.type, elapsed, firstAttack);
        dmg += enchantmentBonus;
        // [CRIT-FX] ENCHANTED_HIT 파티클 조건. 바닐라는 인챈트 보너스가 0 보다 클 때 magicCrit 을 부른다.
        boolean enchanted = enchantmentBonus > 0.0;
        // [GUARDIAN] 가시 반사는 바닐라 Guardian.hurtServer 의 **맨 앞**에 있다 — 피격 무적으로
        // 피해가 거부되어도, 그 타격이 가디언을 죽여도 똑같이 한 번 되돌아간다. 직접 가해자가
        // LivingEntity 일 때만 성립하므로 화살·투사체는 대상이 아니고 근접만 여기서 굴린다.
        int thorns = mob.thornsDamage();
        if (thorns > 0) onThorns.accept(player.nickname(), thorns, mob);
        // [DRAGON] 드래곤은 조준한 부위(EnderDragonPart)가 맞는다: 두뇌의 hurt(part, playerAttack, 피해). 넉백은 두뇌가
        // (앉지 않았을 때) 드래곤 속도에 싣고, 휩쓸기는 부위 개체를 치지 않는다. 무기는 부모 몹을 친 것으로 닳는다.
        if (mob instanceof com.gameexpert.engine.mob.EnderDragon dragon) {
            if (dragonCombat == null) return AttackResult.rejected();
            var hurt = dragonCombat.meleeDragon(player, dragon, dmg);
            if (!hurt.accepted()) return AttackResult.rejected();
            onPlayerHitMob.accept(player.nickname(), mob.id);
            int slot = inv.selectedSlot();
            boolean durable = inv.isDurableSlot(slot);
            if (durable) inv.degrade(slot);
            return new AttackResult(true, durable, mob.id, critical, enchanted, false, mob.x, mob.y, mob.z, false);
        }
        boolean protectedBefore = mob.hurtProtectedAt(tickNo);
        // [MACE] 강타 소리는 피해 판정 직전의 대상 접지 상태를 읽는다(바닐라 hurtEnemy 의 target.onGround()).
        boolean targetOnGround = mob.onGround;
        dmg = FleshWeaponRules.damage(dmg, weapon, mob.type);
        if (!mob.damage(dmg, tickNo, armorEffectivenessDelta)) {
            return AttackResult.rejected(); // 피격 무적으로 완전 무시: 이벤트·내구·넓백 없음
        }
        mob.rememberPlayerKillCredit(player.nickname());

        double ax = mob.x - player.x();
        double az = mob.z - player.z();
        double ad = Math.hypot(ax, az);
        double baseX = 0.0;
        double baseZ = 0.0;
        if (ad > 1e-9) {
            double perTick = CombatRules.KNOCKBACK_HORIZONTAL_BPS / 10.0;
            baseX = ax / ad * perTick;
            baseZ = az / ad * perTick;
        }
        double bonusX = 0.0;
        double bonusZ = 0.0;
        // [SPEAR][B] 창은 스프린트 넉백도 나지 않는다(핀 §5 원문의 같은 문장). 달리며 찔러도
        // 추가 넉백 2차 호출을 건너뛰고 기본 넉백만 남는다.
        // [ENCHANT-WIDE] 밀치기: 26.3 getKnockback = (attack_knockback + 레벨)/2, 질주 공격이면 +0.5.
        // 0.5 단위 하나가 KNOCKBACK_BONUS_BPS 이므로 (레벨 + 질주) 배를 같은 2차 호출에 싣는다.
        int knockbackSteps = com.gameexpert.engine.enchant.EnchantmentRules.meleeKnockbackHalfSteps(
                com.gameexpert.engine.enchant.EnchantmentRules.heldEffectApplies(
                        com.gameexpert.engine.enchant.EnchantmentRules.KNOCKBACK, weapon)
                        ? weaponEnchantments.level(
                                com.gameexpert.engine.enchant.EnchantmentRules.KNOCKBACK) : 0,
                sprinting && !SpearRules.suppressesSprintKnockback(weapon));
        if (knockbackSteps > 0) {
            double perTick = CombatRules.KNOCKBACK_BONUS_BPS / 10.0 * knockbackSteps;
            // 추가 넓백은 가해자 시선 수평 방향의 별도 2차 호출.
            bonusX = -Math.sin(player.yaw()) * perTick;
            bonusZ = -Math.cos(player.yaw()) * perTick;
        }
        // [SPEAR-KINETIC] 창 잽(PiercingWeapon → stabAttack)은 창 피해 유형이 #no_knockback 이라 피격 넉백이 없고
        // 시선 방향 causeExtraKnockback(0.4) + 밀치기만 준다(질주 넉백은 창이 이미 끈다).
        if (!protectedBefore && SpearRules.kinetic(weapon) != null) {
            applyStabKnockback(player, mob, (float) dmg,
                    com.gameexpert.engine.enchant.EnchantmentRules.heldEffectApplies(
                            com.gameexpert.engine.enchant.EnchantmentRules.KNOCKBACK, weapon)
                            ? weaponEnchantments.level(com.gameexpert.engine.enchant.EnchantmentRules.KNOCKBACK)
                            : 0);
        } else
        // [SULFUR-KB] 몸통 블록을 삼킨 유황 큐브는 SulfurCube.knockback 이 가해자 시선·높이각으로
        // 아키타입 넉백 배율을 돌린다(피격 넉백 한 번 + 밀치기·질주 추가 넉백 한 번).
        if (!protectedBefore && mob instanceof com.gameexpert.engine.mob.SulfurCube cube
                && cube.hasBodyItem()) {
            if (applySulfurCubeKnockback(cube, player, (float) dmg, 0.4000000059604645,
                    player.x() - mob.x, player.z() - mob.z, false)) mobSound.accept(cube, "hit");
            if (knockbackSteps > 0 && applySulfurCubeKnockback(cube, player, (float) dmg,
                    knockbackSteps * 0.5, Math.sin(player.yaw()), Math.cos(player.yaw()), true)) {
                mobSound.accept(cube, "hit");
            }
        } else
        // [ENCHANT-WIDE] 바닐라 LivingEntity.knockback 은 strength ·= 1 - 저항 뒤 0 이하이면 아무것도 하지
        // 않는다(철 골렘·워든 1.0, 파괴수 0.75 — MobKnockbackResistance 표).
        if (!protectedBefore && mob.knockbackResistance() < 1.0) {
            // LivingEntity#knockback scales the committed impulse by (1 - knockback_resistance).
            // Keep the scale at this actual melee impulse boundary so armor presentation/state can
            // never diverge from authority physics. The existing grounded vertical term is strength-
            // derived too, so recompute it with the same scale after the shared Mob integrator runs.
            double resistanceScale = Math.max(0.0, 1.0 - mob.knockbackResistance());
            boolean grounded = mob.onGround;
            double previousVy = mob.vy;
            mob.applyKnockback(baseX * resistanceScale, baseZ * resistanceScale, grounded,
                    bonusX * resistanceScale, bonusZ * resistanceScale);
            if (grounded && resistanceScale != 1.0) {
                double vertical = CombatRules.KNOCKBACK_VERTICAL_BPS / 10.0 * resistanceScale;
                mob.vy = Math.min(vertical, previousVy / 2.0 + vertical);
            }
        }
        // [ENCHANT-WIDE] 휩쓸기 공격(바닐라 Player.attack → isSweepAttack → doSweepAttack). 주 대상 명중이
        // 받아들여진 뒤, 주 대상의 post_attack 효과보다 먼저 돈다.
        float strength = (float) CombatRules.attackStrengthScale(weapon, elapsed, firstAttack);
        boolean sweep = com.gameexpert.engine.enchant.EnchantmentRules.isSweepAttack(weapon,
                strength, critical, sprinting, !player.airborne(),
                player.recentHorizontalSpeedPerMcTick(tickNo),
                com.gameexpert.engine.enchant.EnchantmentRules.PLAYER_BASE_MOVEMENT_SPEED
                        * player.statusEffects().speedMultiplier());
        if (sweep) {
            doSweepAttack(player, mob, weapon, weaponEnchantments, sweepAttackDamage, strength, tickNo);
        }
        applyPostAttackEnchantments(mob, weapon, weaponEnchantments);
        boolean lethal = mob.isDead();
        onPlayerHitMob.accept(player.nickname(), mob.id);
        if (lethal) {
            // 약탈 레벨은 이 명중을 낸 무기에서 읽는다(바닐라 getMobLooting 과 같은 순간·같은 손).
            onDeath.accept(mob, "death",
                    CombatRules.lootingLevel(weapon, inv.enchantments(inv.selectedSlot())));
        } else {
            // neutral guardians retain attacker identity; hurtByPlayer also records the
            // [ROTTEN-LEATHER] per-entity grudge that ends undead neutrality for this attacker.
            mob.hurtByPlayer(player.nickname(), player.x(), player.z());
            if (mob.type == MobType.BEE) {
                angerNearbyBees(mob, player.nickname(), player.x(), player.z());
            }
            if (mob.type == MobType.ZOMBIE_PIGMAN) {
                angerNearbyPigmen(mob, player.nickname());
            }
        }
        int slot = inv.selectedSlot();
        // [MACE] 강타 사실(바닐라 hurtEnemy·wind_burst·postHurtEnemy 는 런타임이 MobSystem.applyMaceSmash 로
        // 실행한다 — 주변 플레이어·폭발 노출·소리·영속이 그쪽 소유다).
        MaceSmash maceSmash = smash
                ? new MaceSmash(mob.id, fallDistance, targetOnGround, heldLevel(weapon,
                        weaponEnchantments, com.gameexpert.engine.enchant.EnchantmentRules.WIND_BURST))
                : null;
        if (inv.isDurableSlot(slot)) {
            inv.degrade(slot); // 공격에 사용한 검/도구는 유효 명중마다 1 마모
            return new AttackResult(true, true, mob.id, critical, enchanted, lethal, mob.x, mob.y, mob.z,
                    sweep, maceSmash);
        }
        return new AttackResult(true, false, mob.id, critical, enchanted, lethal, mob.x, mob.y, mob.z,
                sweep, maceSmash);
    }

    /** [MACE] 손에 든 무기에서 효과가 도는 인챈트 레벨(heldEffectApplies 게이트). */
    private static int heldLevel(short weapon,
            com.gameexpert.engine.enchant.WideEnchantments enchantments, int enchantId) {
        return com.gameexpert.engine.enchant.EnchantmentRules.heldEffectApplies(enchantId, weapon)
                ? enchantments.level(enchantId) : 0;
    }

    /**
     * [MACE] 받아들여진 강타 한 건: 대상 id, 강타 순간의 공격자 낙하 거리, 대상 접지 여부(소리 선택),
     * 돌풍 레벨.
     */
    record MaceSmash(long targetId, double fallDistance, boolean targetOnGround, int windBurstLevel) {}

    /**
     * [ENCHANT-WIDE] 바닐라 {@code Player.doSweepAttack}. 주 대상 AABB 를 (1, 0.25, 1) 부풀린 상자에 걸리고
     * 공격자와의 거리² 가 9 미만인 다른 몹마다 {@code (1 + 비율 × 피해 + 대상별 인챈트 추가) × 세기} 를
     * 주고, 받아들여지면 공격자 시선 방향 0.4 넉백(저항 적용)과 post_attack 인챈트를 건다. 내구·허기는
     * 더 쓰지 않는다. 가디언 가시는 직접 가해자가 플레이어이므로 휩쓸린 가디언에서도 돈다.
     */
    private void doSweepAttack(PlayerTickState player, Mob target, short weapon,
            com.gameexpert.engine.enchant.WideEnchantments weaponEnchantments,
            double attackDamage, float strength, long tickNo) {
        int sweeping = com.gameexpert.engine.enchant.EnchantmentRules.heldEffectApplies(
                com.gameexpert.engine.enchant.EnchantmentRules.SWEEPING_EDGE, weapon)
                ? weaponEnchantments.level(com.gameexpert.engine.enchant.EnchantmentRules.SWEEPING_EDGE)
                : 0;
        double sweepDamage = com.gameexpert.engine.enchant.EnchantmentRules.sweepBaseDamage(
                sweeping, attackDamage);
        double halfW = target.width() / 2.0
                + com.gameexpert.engine.enchant.EnchantmentRules.SWEEP_INFLATE_HORIZONTAL;
        double minY = target.y - com.gameexpert.engine.enchant.EnchantmentRules.SWEEP_INFLATE_VERTICAL;
        double maxY = target.y + target.height()
                + com.gameexpert.engine.enchant.EnchantmentRules.SWEEP_INFLATE_VERTICAL;
        double forwardX = -Math.sin(player.yaw());
        double forwardZ = -Math.cos(player.yaw());
        for (Mob swept : List.copyOf(mobs)) {
            if (swept == target || swept.isDead() || swept.removed) continue;
            // [EC-MOBS] 휩쓸기는 LivingEntity 만 맞힌다(아이템 액자·엔드 수정 제외).
            if (swept.type == MobType.ITEM_FRAME || swept.type == MobType.END_CRYSTAL) continue;
            double sweptHalf = swept.width() / 2.0;
            if (swept.x + sweptHalf <= target.x - halfW || swept.x - sweptHalf >= target.x + halfW
                    || swept.z + sweptHalf <= target.z - halfW
                    || swept.z - sweptHalf >= target.z + halfW
                    || swept.y + swept.height() <= minY || swept.y >= maxY) {
                continue;
            }
            double dx = swept.x - player.x();
            double dy = swept.y - player.y();
            double dz = swept.z - player.z();
            if (!(dx * dx + dy * dy + dz * dz
                    < com.gameexpert.engine.enchant.EnchantmentRules.SWEEP_MAX_DISTANCE_SQ)) continue;
            double damage = (sweepDamage
                    + com.gameexpert.engine.enchant.EnchantmentRules.meleeEnchantmentBonusMilli(
                            weapon, weaponEnchantments,
                            com.gameexpert.engine.mob.UndeadNeutralityRules.isUndead(swept.type),
                            com.gameexpert.engine.enchant.EnchantmentRules.isArthropod(swept.type),
                            com.gameexpert.engine.enchant.EnchantmentRules.isAquatic(swept.type))
                    / (double) com.gameexpert.engine.enchant.EnchantmentRules.MILLI) * strength;
            int thorns = swept.thornsDamage();
            if (thorns > 0) onThorns.accept(player.nickname(), thorns, swept);
            if (!swept.damage(damage, tickNo)) continue;
            swept.rememberPlayerKillCredit(player.nickname());
            double resistanceScale = Math.max(0.0, 1.0 - swept.knockbackResistance());
            if (swept instanceof com.gameexpert.engine.mob.SulfurCube cube && cube.hasBodyItem()) {
                // [SULFUR-KB] 휩쓸린 큐브: hurtServer 의 피격 넉백(가해자 − 대상) 뒤 doSweepAttack 의
                // knockback(0.4, sin(yRot), −cos(yRot), 피해) 가 둘 다 SulfurCube.knockback 이다.
                if (applySulfurCubeKnockback(cube, player, (float) damage, 0.4000000059604645,
                        player.x() - swept.x, player.z() - swept.z, false)) mobSound.accept(cube, "hit");
                if (applySulfurCubeKnockback(cube, player, (float) damage, 0.4000000059604645,
                        Math.sin(player.yaw()), Math.cos(player.yaw()), false)) mobSound.accept(cube, "hit");
            } else if (resistanceScale > 0.0) {
                double perTick = CombatRules.KNOCKBACK_HORIZONTAL_BPS / 10.0 * resistanceScale;
                boolean grounded = swept.onGround;
                double previousVy = swept.vy;
                swept.applyKnockback(forwardX * perTick, forwardZ * perTick, grounded, 0.0, 0.0);
                if (grounded && resistanceScale != 1.0) {
                    double vertical = CombatRules.KNOCKBACK_VERTICAL_BPS / 10.0 * resistanceScale;
                    swept.vy = Math.min(vertical, previousVy / 2.0 + vertical);
                }
            }
            applyPostAttackEnchantments(swept, weapon, weaponEnchantments);
            boolean lethal = swept.isDead();
            onPlayerHitMob.accept(player.nickname(), swept.id);
            broadcast.accept(new WsMessages.MobHurt(swept.id, lethal));
            if (lethal) {
                onDeath.accept(swept, "death", CombatRules.lootingLevel(weapon,
                        player.inventory().enchantments(player.inventory().selectedSlot())));
            } else {
                swept.hurtByPlayer(player.nickname(), player.x(), player.z());
                if (swept.type == MobType.BEE) {
                    angerNearbyBees(swept, player.nickname(), player.x(), player.z());
                }
                if (swept.type == MobType.ZOMBIE_PIGMAN) {
                    angerNearbyPigmen(swept, player.nickname());
                }
            }
        }
    }

    /**
     * [ENCHANT-WIDE] 근접 명중의 post_attack 인챈트(직접 피해이므로 is_direct 조건 성립).
     * 발화: {@code ignite 4·level 초}(연소 중이면 긴 쪽). 치명타로 죽은 대상도 먼저 불붙여 두면 사망
     * 드랍이 기존 연소 판정으로 익혀진다 — 바닐라는 같은 결과를 #smelts_loot(발화) 무기 조건으로 낸다.
     * 살충: 절지동물에 구속 IV, {@code round(randomBetween(1.5, 1.5+0.5(level-1)) × 20)} MC 틱.
     */
    private void applyPostAttackEnchantments(Mob mob,
            short weapon, com.gameexpert.engine.enchant.WideEnchantments weaponEnchantments) {
        int fireAspect = com.gameexpert.engine.enchant.EnchantmentRules.heldEffectApplies(
                com.gameexpert.engine.enchant.EnchantmentRules.FIRE_ASPECT, weapon)
                ? weaponEnchantments.level(com.gameexpert.engine.enchant.EnchantmentRules.FIRE_ASPECT)
                : 0;
        if (fireAspect > 0 && !mob.fireImmune()) {
            mob.igniteForTicks(mcTicksToServerTicks(
                    com.gameexpert.engine.enchant.EnchantmentRules.fireAspectIgniteMcTicks(fireAspect)));
        }
        int bane = com.gameexpert.engine.enchant.EnchantmentRules.heldEffectApplies(
                com.gameexpert.engine.enchant.EnchantmentRules.BANE_OF_ARTHROPODS, weapon)
                ? weaponEnchantments.level(
                        com.gameexpert.engine.enchant.EnchantmentRules.BANE_OF_ARTHROPODS)
                : 0;
        if (bane > 0 && com.gameexpert.engine.enchant.EnchantmentRules.isArthropod(mob.type)
                && !mob.isDead()) {
            int mcTicks = com.gameexpert.engine.enchant.EnchantmentRules.baneSlownessMcTicks(
                    bane, enchantmentRandom.nextFloat());
            // 바닐라 틱수를 그대로 옮긴다(홀수 MC 틱을 서버 틱으로 반올림하지 않는다).
            mob.statusEffects().applyMcTicks(com.gameexpert.engine.effect.StatusEffect.SLOWNESS,
                    com.gameexpert.engine.enchant.EnchantmentRules.BANE_SLOWNESS_AMPLIFIER,
                    mcTicks);
        }
    }

    /** 20 TPS 바닐라 틱을 10 TPS 권위 틱으로(올림). */
    private static int mcTicksToServerTicks(int mcTicks) {
        return (mcTicks + 1) / 2;
    }

    /** [ENCHANT-WIDE] post_attack 인챈트 굴림(살충 지속). 테스트는 시드를 고정한다. */
    void setEnchantmentRandomForTest(java.util.Random random) {
        this.enchantmentRandom = random;
    }

    /**
     * [ENCHANT-WIDE] 창 찌르기 한 번(바닐라 {@code handlePlayerAction(STAB)} → {@code PiercingWeapon.attack}).
     * 주손이 창이고 충전이 찼을 때만 받아들이며, 받아들이면 공격 세기를 초기화한다
     * ({@code swingAndResetAttackStrength}). 돌진 인챈트 요건이 맞으면 내구 1 과 허기 4·L 을 쓴다.
     *
     * @return 내구가 바뀌어 개인 인벤토리 갱신이 필요하면 true
     */
    boolean handleSpearStab(PlayerTickState player, long tickNo, boolean riding, boolean inWater) {
        if (player == null || player.isDead()) return false;
        var inv = player.inventory();
        int slot = inv.selectedSlot();
        short weapon = inv.itemType(slot);
        if (!com.gameexpert.engine.enchant.EnchantmentRules.isSpearItem(weapon)) return false;
        Long last = lastSpearStabTick.get(player.nickname());
        if (last != null && !com.gameexpert.engine.enchant.EnchantmentRules.spearStabCharged(
                SpearRules.attackSpeed(weapon), Math.max(0L, tickNo - last))) {
            return false;
        }
        lastSpearStabTick.put(player.nickname(), tickNo);
        lastAttackTick.put(player.nickname(), tickNo);
        int lunge = com.gameexpert.engine.enchant.EnchantmentRules.heldEffectApplies(
                com.gameexpert.engine.enchant.EnchantmentRules.LUNGE, weapon)
                ? inv.wideEnchantments(slot).level(com.gameexpert.engine.enchant.EnchantmentRules.LUNGE)
                : 0;
        // [SPEAR-KINETIC] PiercingWeapon.attack: postPiercingAttack(돌진 인챈트 play_sound) → 명중이면 hitSound(근접
        // 경로가 이미 냈다) → 늘 sound(item.spear(_wood).attack).
        boolean lunges = com.gameexpert.engine.enchant.EnchantmentRules.lungeApplies(
                lunge, riding, player.gliding(), inWater, player.food());
        if (lunges) playerSound.accept(player, SpearRules.lungeSoundKind(lunge));
        playerSound.accept(player, SpearRules.soundKind(weapon, "attack"));
        if (!lunges) {
            return false;
        }
        player.addExhaustion(com.gameexpert.engine.enchant.EnchantmentRules.lungeExhaustionMilli(lunge));
        if (!inv.isDurableSlot(slot)) return false;
        inv.degrade(slot);
        return true;
    }

    /**
     * [SULFUR-KB] {@code SulfurCube.knockback} 의 가해 개체({@code DamageSource.getEntity()}): 발 위치,
     * 눈 높이, 시선({@code getLookAngle}). 투사체 피해에서는 쏜 개체다.
     */
    @lombok.Value
    @lombok.experimental.Accessors(fluent = true)
    static class SulfurAttacker {
        double x;
        double y;
        double z;
        double eyeY;
        double lookX;
        double lookY;
        double lookZ;


        static SulfurAttacker of(PlayerTickState player) {
            double[] look = com.gameexpert.engine.mob.SulfurCubeKnockback.lookVector(
                    player.yaw(), player.pitch());
            return new SulfurAttacker(player.x(), player.y(), player.z(),
                    player.y() + (player.crouching()
                            ? PlayerInteractionRules.CROUCHING_EYE_HEIGHT
                            : PlayerInteractionRules.STANDING_EYE_HEIGHT),
                    look[0], look[1], look[2]);
        }

        /**
         * 몹 가해자. 근접·사격 목표를 향한 몹은 LookControl 이 머리를 목표의 눈에 맞추므로(MeleeAttackGoal
         * · RangedAttackGoal 의 lookAt) 시선은 가해자 눈 → 대상 눈 방향이다.
         */
        static SulfurAttacker of(Mob attacker, Mob target) {
            double eyeY = attacker.y + attacker.eyeHeight();
            double dx = target.x - attacker.x;
            double dy = target.y + target.eyeHeight() - eyeY;
            double dz = target.z - attacker.z;
            double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (length < 1.0E-4) return new SulfurAttacker(attacker.x, attacker.y, attacker.z, eyeY, 0, 0, 0);
            return new SulfurAttacker(attacker.x, attacker.y, attacker.z, eyeY,
                    dx / length, dy / length, dz / length);
        }
    }

    /**
     * [SULFUR-KB] {@code SulfurCube.knockback} 한 번. 가해자 눈 높이·시선과 큐브 중심·높이각이 아키타입
     * 배율을 돌리고, 결과(블록/MC틱)를 10 TPS 속도로 옮겨(×2) 기존 속도에 더한다.
     *
     * @return 몸통 블록이 있어 큐브 넉백을 적용했으면 참. 참이면 호출부가 아키타입 {@code hit_sound} 를 낸다.
     */
    static boolean applySulfurCubeKnockback(com.gameexpert.engine.mob.SulfurCube cube,
            PlayerTickState player, float damage, double strength, double xRatio, double zRatio,
            boolean extra) {
        return applySulfurCubeKnockback(cube, SulfurAttacker.of(player), damage, strength,
                xRatio, zRatio, extra);
    }

    static boolean applySulfurCubeKnockback(com.gameexpert.engine.mob.SulfurCube cube,
            SulfurAttacker attacker, float damage, double strength, double xRatio, double zRatio,
            boolean extra) {
        com.gameexpert.engine.mob.SulfurCubeRules.Archetype archetype = cube.archetype();
        if (archetype == null) return false;
        com.gameexpert.engine.mob.SulfurCubeKnockback.Impulse impulse =
                com.gameexpert.engine.mob.SulfurCubeKnockback.impulse(archetype.horizontalPower(),
                        archetype.verticalPower(), strength, xRatio, zRatio, damage, extra,
                        cube.knockbackResistance(), attacker.x(), attacker.eyeY(), attacker.z(),
                        attacker.lookX(), attacker.lookY(), attacker.lookZ(),
                        attacker.x(), attacker.y(), attacker.z(),
                        cube.x, cube.y, cube.z, (float) cube.height());
        cube.applyAdditiveKnockback(impulse.dvx() * 2.0, impulse.dvy() * 2.0, impulse.dvz() * 2.0);
        return true;
    }

    // ── [SPEAR-KINETIC] 창 돌진(바닐라 KineticWeapon) ──────────────────────────────

    /**
     * [SPEAR-KINETIC] 창을 쓰는 중인 플레이어 한 명의 돌진 상태. 바닐라는 {@code startUsingItem} 뒤 매 MC 틱
     * {@code ItemStack.onUseTick → KineticWeapon.damageEntities(stack, remainingUseTicks, entity, slot)} 를 돈다.
     * 이 권위는 10 TPS 라 권위 틱마다 MC 틱 둘을 같은 위치로 평가한다. 대상별 마지막 찌른 MC 틱은
     * {@code LivingEntity.recentKineticEnemies}(접촉 쿨다운 10 MC 틱)다.
     */
    static final class KineticUse {
        final PlayerInventory.Hand hand;
        final short itemType;
        final long startTick;
        double lastX;
        double lastY;
        double lastZ;
        boolean hasLast;
        final Map<Long, Long> stabbedAtMcTick = new HashMap<>();

        KineticUse(PlayerInventory.Hand hand, short itemType, long startTick) {
            this.hand = hand;
            this.itemType = itemType;
            this.startTick = startTick;
        }
    }

    /** [SPEAR-KINETIC] 한 권위 틱의 돌진 결과: 찌른 몹 수(명중 피드백 · 소리), 내구가 줄었는지, 명중 목록. */
    @lombok.Value
    @lombok.experimental.Accessors(fluent = true)
    static class KineticTick {
        int stabs;
        boolean inventoryChanged;
        boolean enchanted;
        short itemType;
        List<KineticHitMob> hits;

        static final KineticTick NONE = new KineticTick(0, false, false, (short) 0, List.of());
    }

    /** [SPEAR-KINETIC] 돌진 명중 한 건(공격자의 combatHit stab 파티클 자리). */
    @lombok.Value
    @lombok.experimental.Accessors(fluent = true)
    static class KineticHitMob {
        long mobId;
        double x;
        double y;
        double z;
        boolean enchanted;
}

    private final Map<String, KineticUse> kineticUses = new HashMap<>();

    /** [SPEAR-KINETIC] 창 쓰기 시작(우클릭 누름). 창이 아니면 거절한다. */
    boolean startKineticUse(PlayerTickState player, PlayerInventory.Hand hand, long tickNo) {
        var inv = player.inventory();
        short item = inv.stack(inv.capture(hand)).itemType();
        if (SpearRules.kinetic(item) == null) return false;
        KineticUse use = new KineticUse(hand, item, tickNo);
        use.lastX = player.x();
        use.lastY = player.y();
        use.lastZ = player.z();
        use.hasLast = true;
        kineticUses.put(player.nickname(), use);
        player.setSpearUsing(true);
        // KineticWeapon.makeSound: 쓰기 시작 순간(Item.use) item.spear(_wood).use.
        playerSound.accept(player, SpearRules.soundKind(item, "use"));
        return true;
    }

    /** [SPEAR-KINETIC] 창 쓰기 끝(우클릭 뗌 · 손 바뀜 · 사망). */
    boolean stopKineticUse(PlayerTickState player) {
        player.setSpearUsing(false);
        return kineticUses.remove(player.nickname()) != null;
    }

    boolean kineticUsing(String nickname) {
        return kineticUses.containsKey(nickname);
    }

    /** [SPEAR-KINETIC] 쓰는 손. 쓰지 않으면 null. */
    PlayerInventory.Hand kineticHand(String nickname) {
        KineticUse use = kineticUses.get(nickname);
        return use == null ? null : use.hand;
    }

    /** [SPEAR-KINETIC] 퇴장한 플레이어의 쓰기 상태를 버린다(다시 들어오면 새 쓰기로 시작한다). */
    void retainKineticUses(java.util.Set<String> present) {
        kineticUses.keySet().retainAll(present);
    }

    /** [SPEAR-KINETIC] 쓰기 시작 뒤 흐른 MC 틱(3인칭 · 1인칭 쓰기 자세). 쓰지 않으면 -1. */
    long kineticUseMcTicks(String nickname, long tickNo) {
        KineticUse use = kineticUses.get(nickname);
        return use == null ? -1L : Math.max(0L, tickNo - use.startTick) * 2L;
    }

    /**
     * [SPEAR-KINETIC] 권위 틱 한 번의 {@code KineticWeapon.damageEntities} 두 번(MC 틱 2n · 2n+1). {@code useTicks <
     * delayTicks} 면 아무것도 하지 않고, 아니면 {@code t = useTicks − delay} 로 하마·넉백·피해 조건을 시험한다.
     * 공격자 속도는 이번 권위 틱의 실제 이동(블록/MC틱 × 20), 대상 속도는 몹 속도다(시선 방향 성분).
     */
    KineticTick tickKineticUse(PlayerTickState player, com.gameexpert.engine.mob.MobWorldView world, long tickNo) {
        KineticUse use = kineticUses.get(player.nickname());
        if (use == null) return KineticTick.NONE;
        var inv = player.inventory();
        PlayerInventory.HandRef handRef = inv.capture(use.hand);
        if (player.isDead() || inv.stack(handRef).itemType() != use.itemType) {
            kineticUses.remove(player.nickname());
            player.setSpearUsing(false);
            return KineticTick.NONE;
        }
        SpearRules.Kinetic kinetic = SpearRules.kinetic(use.itemType);
        double moveX = use.hasLast ? (player.x() - use.lastX) / 2.0 : 0.0;
        double moveY = use.hasLast ? (player.y() - use.lastY) / 2.0 : 0.0;
        double moveZ = use.hasLast ? (player.z() - use.lastZ) / 2.0 : 0.0;
        use.lastX = player.x();
        use.lastY = player.y();
        use.lastZ = player.z();
        use.hasLast = true;
        double[] look = com.gameexpert.engine.mob.SulfurCubeKnockback.lookVector(player.yaw(), player.pitch());
        double eyeY = player.y() + (player.crouching()
                ? PlayerInteractionRules.CROUCHING_EYE_HEIGHT : PlayerInteractionRules.STANDING_EYE_HEIGHT);
        // getMotion: 플레이어는 자기 알려진 속도(블록/틱) × 20 = 블록/초.
        double attackerSpeed = (look[0] * moveX + look[1] * moveY + look[2] * moveZ) * 20.0;
        int stabs = 0;
        boolean inventoryChanged = false;
        boolean enchanted = false;
        List<KineticHitMob> hits = new ArrayList<>();
        long elapsed = Math.max(0L, tickNo - use.startTick);
        for (int sub = 0; sub < 2; sub++) {
            long useTicks = elapsed * 2L + sub;
            if (useTicks < kinetic.delayTicks()) continue;
            if (inv.stack(inv.capture(use.hand)).itemType() != use.itemType) break;
            int t = (int) (useTicks - kinetic.delayTicks());
            // AttackRange: 눈 + 시선 × 2.0 → 눈 + 시선 × (4.5 + max(0, 알려진 이동·시선)), 여유 0.125.
            double forward = Math.max(0.0, look[0] * moveX + look[1] * moveY + look[2] * moveZ);
            double minX = player.x() + look[0] * SpearRules.SPEAR_MIN_REACH;
            double minY = eyeY + look[1] * SpearRules.SPEAR_MIN_REACH;
            double minZ = player.z() + look[2] * SpearRules.SPEAR_MIN_REACH;
            double maxReach = SpearRules.SPEAR_REACH + forward;
            double maxX = player.x() + look[0] * maxReach;
            double maxY = eyeY + look[1] * maxReach;
            double maxZ = player.z() + look[2] * maxReach;
            // 눈과 최소 리치 사이가 막히면(블록 명중이 최소 리치보다 가깝다) 아무도 맞지 않는다.
            if (world != null && !world.hasLineOfSight(player.x(), eyeY, player.z(), minX, minY, minZ)) continue;
            for (Mob mob : new ArrayList<>(mobs)) {
                // 아이템 액자 · 엔드 수정은 LivingEntity 가 아니다. 드래곤은 부위(EnderDragonPart) 상자로 맞는다.
                if (mob.isDead() || mob.removed || mob.type == MobType.ITEM_FRAME
                        || mob.type == MobType.END_CRYSTAL) continue;
                double hitT = kineticTargetT(mob, minX, minY, minZ, maxX, maxY, maxZ);
                if (hitT < 0.0) continue;
                // 블록이 명중점보다 앞이면 그 몹은 가려진다(clip 이 끝점을 블록에서 자른다).
                if (world != null && !world.hasLineOfSight(player.x(), eyeY, player.z(),
                        minX + (maxX - minX) * hitT, minY + (maxY - minY) * hitT,
                        minZ + (maxZ - minZ) * hitT)) continue;
                // wasRecentlyStabbed(target, contactCooldownTicks) · rememberStabbedEntity.
                Long last = use.stabbedAtMcTick.get(mob.id);
                if (last != null && useTicks - last < SpearRules.KINETIC_CONTACT_COOLDOWN_TICKS) continue;
                use.stabbedAtMcTick.put(mob.id, useTicks);
                double targetSpeed = (look[0] * mob.horizontalVx + look[1] * mob.vy
                        + look[2] * mob.horizontalVz) * 10.0;
                double relative = Math.max(0.0, attackerSpeed - targetSpeed);
                double factor = SpearRules.KINETIC_PLAYER_SPEED_FACTOR;
                boolean dismount = SpearRules.kineticDismounts(kinetic, t, attackerSpeed, factor);
                boolean knockback = SpearRules.kineticKnocksBack(kinetic, t, attackerSpeed, factor);
                boolean damage = SpearRules.kineticDamages(kinetic, t, relative, factor);
                if (!dismount && !knockback && !damage) continue;
                float amount = SpearRules.kineticDamage(1.0, relative, kinetic.damageMultiplier());
                KineticStab stab = mob instanceof com.gameexpert.engine.mob.EnderDragon dragon
                        ? kineticStabDragon(player, use.hand, dragon, amount, damage)
                        : kineticStab(player, use.hand, mob, amount, damage, knockback, dismount, tickNo);
                if (stab.landed()) {
                    stabs++;
                    inventoryChanged |= stab.inventoryChanged();
                    enchanted |= stab.enchanted();
                    hits.add(new KineticHitMob(mob.id, mob.x, mob.y, mob.z, stab.enchanted()));
                }
                // 찌른 뒤 창이 부서졌으면(hurtEnemy) 쓰기가 끝난다 — 남은 대상은 찌르지 않는다.
                if (inv.stack(inv.capture(use.hand)).itemType() != use.itemType) break;
            }
        }
        return stabs == 0 && !inventoryChanged ? KineticTick.NONE
                : new KineticTick(stabs, inventoryChanged, enchanted, use.itemType, List.copyOf(hits));
    }

    /** [SPEAR-KINETIC] 찌르기 한 건의 결과. */
    @lombok.Value
    @lombok.experimental.Accessors(fluent = true)
    static class KineticStab {
        boolean landed;
        boolean inventoryChanged;
        boolean enchanted;
}

    /**
     * [SPEAR-KINETIC] 찌르기 선분이 대상에 처음 닿는 비율(−1 이면 빗나감). 몸 상자를 0.125 부풀리고, 드래곤은 몸 대신 부위
     * 상자 여덟({@code EnderDragonPart}, 같은 여유) 중 가장 먼저 닿는 것이다.
     */
    static double kineticTargetT(Mob mob, double x0, double y0, double z0, double x1, double y1, double z1) {
        double pad = SpearRules.SPEAR_HITBOX_INFLATION;
        if (mob instanceof com.gameexpert.engine.mob.EnderDragon dragon) {
            double[][] parts = dragon.partHitBoxes();
            if (parts == null) return -1.0;
            double best = -1.0;
            for (double[] box : parts) {
                double t = segmentBoxT(x0, y0, z0, x1, y1, z1, box[0] - pad, box[1] - pad, box[2] - pad,
                        box[3] + pad, box[4] + pad, box[5] + pad);
                if (t >= 0.0 && (best < 0.0 || t < best)) best = t;
            }
            return best;
        }
        return segmentBoxT(x0, y0, z0, x1, y1, z1,
                mob.x - mob.width() / 2.0 - pad, mob.y - pad, mob.z - mob.width() / 2.0 - pad,
                mob.x + mob.width() / 2.0 + pad, mob.y + mob.height() + pad, mob.z + mob.width() / 2.0 + pad);
    }

    /**
     * [SPEAR-KINETIC] 드래곤 찌르기: 부위 명중은 부모로 바뀌어 몸통 피해({@code hurt(body, playerAttack, 피해)})가 된다.
     * 인챈트 보너스는 충전 배율 없이 더하고, 받아들여지면 창이 1 닳는다(부모 드래곤의 hurtEnemy). 넉백·하마는 두뇌 몫이다.
     */
    private KineticStab kineticStabDragon(PlayerTickState player, PlayerInventory.Hand usedHand,
            com.gameexpert.engine.mob.EnderDragon dragon, float damage, boolean dealsDamage) {
        if (!dealsDamage || dragonCombat == null) return new KineticStab(false, false, false);
        var inv = player.inventory();
        PlayerInventory.HandRef hand = inv.capture(usedHand);
        short weapon = inv.stack(hand).itemType();
        double bonus = com.gameexpert.engine.enchant.EnchantmentRules.meleeEnchantmentBonusMilli(
                weapon, inv.stack(hand).wideEnchantments(), false, false, false)
                / (double) com.gameexpert.engine.enchant.EnchantmentRules.MILLI;
        var result = dragonCombat.kineticDragon(player, dragon, damage + bonus);
        if (!result.accepted()) return new KineticStab(false, false, false);
        onPlayerHitMob.accept(player.nickname(), dragon.id);
        player.addExhaustion(100);
        PlayerInventory.StackSnapshot before = inv.stack(hand);
        inv.degrade(hand);
        boolean worn = !inv.stack(inv.capture(usedHand)).equals(before);
        return new KineticStab(true, worn, bonus > 0.0);
    }

    /**
     * [SPEAR-KINETIC] 바닐라 {@code Player.stabAttack(slot, target, damage, dealsDamage, dealsKnockback, dismount)}: 인챈트
     * 보너스는 쓰는 손이라 충전 배율 없이 더하고, 피해 → 넉백(시선 방향 0.4, 이어 밀치기 인챈트 extra) → 하마 순서다.
     * 피해를 줬으면 무기가 1 닳고(hurtEnemy) 허기 0.1 을 쓴다. 피해·넉백·하마가 모두 없으면 명중이 아니다.
     */
    private KineticStab kineticStab(PlayerTickState player, PlayerInventory.Hand usedHand, Mob mob, float damage,
            boolean dealsDamage, boolean dealsKnockback, boolean dismount, long tickNo) {
        var inv = player.inventory();
        PlayerInventory.HandRef hand = inv.capture(usedHand);
        short weapon = inv.stack(hand).itemType();
        com.gameexpert.engine.enchant.WideEnchantments enchantments = inv.stack(hand).wideEnchantments();
        double bonus = com.gameexpert.engine.enchant.EnchantmentRules.meleeEnchantmentBonusMilli(
                weapon, enchantments,
                com.gameexpert.engine.mob.UndeadNeutralityRules.isUndead(mob.type),
                com.gameexpert.engine.enchant.EnchantmentRules.isArthropod(mob.type),
                com.gameexpert.engine.enchant.EnchantmentRules.isAquatic(mob.type))
                / (double) com.gameexpert.engine.enchant.EnchantmentRules.MILLI;
        double total = dealsDamage ? FleshWeaponRules.damage(damage + bonus, weapon, mob.type) : 0.0;
        boolean hurt = false;
        if (dealsDamage) {
            int thorns = mob.thornsDamage();
            if (thorns > 0) onThorns.accept(player.nickname(), thorns, mob);
            hurt = mob.damage(total, tickNo);
            if (hurt) mob.rememberPlayerKillCredit(player.nickname());
        }
        // 하마(stopRiding)는 바닐라에서 넉백 뒤지만 속도를 건드리지 않는다. 이 권위의 탈것 해제는 승객 속도를
        // 0 으로 두므로 먼저 내려 두어야 넉백이 남는다(결과가 바닐라 순서와 같다).
        boolean dismounted = false;
        if (dismount) dismounted = mob.dismountAuthoritativeVehicle();
        if (dealsKnockback && !mob.isDead()) {
            int knockbackLevel = com.gameexpert.engine.enchant.EnchantmentRules.heldEffectApplies(
                    com.gameexpert.engine.enchant.EnchantmentRules.KNOCKBACK, weapon)
                    ? enchantments.level(com.gameexpert.engine.enchant.EnchantmentRules.KNOCKBACK) : 0;
            applyStabKnockback(player, mob, (float) total, knockbackLevel);
        }
        if (!hurt && !dealsKnockback && !dismounted) return new KineticStab(false, false, false);
        boolean lethal = mob.isDead();
        onPlayerHitMob.accept(player.nickname(), mob.id);
        // 피해 없는 찌르기(넉백 · 하마만)는 피격 연출이 없다(hurtOrSimulate 가 돌지 않았다).
        if (hurt) broadcast.accept(new WsMessages.MobHurt(mob.id, lethal));
        if (lethal) {
            onDeath.accept(mob, "death", CombatRules.lootingLevel(weapon, inv.stack(hand).enchantments()));
        } else if (hurt) {
            mob.hurtByPlayer(player.nickname(), player.x(), player.z());
            if (mob.type == MobType.BEE) angerNearbyBees(mob, player.nickname(), player.x(), player.z());
            if (mob.type == MobType.ZOMBIE_PIGMAN) angerNearbyPigmen(mob, player.nickname());
        }
        if (hurt) applyPostAttackEnchantments(mob, weapon, enchantments);
        player.addExhaustion(100);
        boolean worn = false;
        if (hurt) {
            PlayerInventory.StackSnapshot before = inv.stack(hand);
            inv.degrade(hand);
            worn = !inv.stack(inv.capture(usedHand)).equals(before);
        }
        return new KineticStab(true, worn, bonus > 0.0);
    }

    /**
     * [SPEAR-KINETIC] 창 찌르기의 넉백. 창 피해 유형({@code minecraft:spear})은 {@code #no_knockback} 이라 피격 넉백이 없고,
     * {@code stabAttack} 이 {@code causeExtraKnockback(target, 0.4f, …)} 와 이어 {@code getKnockback}(밀치기 레벨 × 0.5)을
     * 공격자 시선 수평 방향({@code sin(yRot), −cos(yRot)})으로 준다. 몸통 블록 큐브는 SulfurCube.knockback 이다.
     */
    void applyStabKnockback(PlayerTickState player, Mob mob, float damage, int knockbackLevel) {
        int cubeHits = applyFacingStabKnockback(mob, -Math.sin(player.yaw()), -Math.cos(player.yaw()),
                SulfurAttacker.of(player), damage, knockbackLevel);
        for (int hit = 0; hit < cubeHits; hit++) mobSound.accept(mob, "hit");
    }

    /**
     * [SPEAR-KINETIC] 시선 수평 방향({@code forwardX, forwardZ}) 찌르기 넉백 — 플레이어 · 몹 가해자 공용. 몸통 블록 큐브는
     * {@code SulfurCube.knockback}(비율은 시선의 반대: sin(yRot), cos(yRot))이고 큐브 넉백 횟수를 돌려준다.
     */
    static int applyFacingStabKnockback(Mob mob, double forwardX, double forwardZ, SulfurAttacker source,
            float damage, int knockbackLevel) {
        if (mob instanceof com.gameexpert.engine.mob.SulfurCube cube && cube.hasBodyItem()) {
            int hits = 0;
            if (applySulfurCubeKnockback(cube, source, damage, 0.4000000059604645, -forwardX, -forwardZ, false)) hits++;
            if (knockbackLevel > 0 && applySulfurCubeKnockback(cube, source, damage, knockbackLevel * 0.5,
                    -forwardX, -forwardZ, true)) hits++;
            return hits;
        }
        double resistanceScale = Math.max(0.0, 1.0 - mob.knockbackResistance());
        if (resistanceScale <= 0.0) return 0;
        double base = CombatRules.KNOCKBACK_HORIZONTAL_BPS / 10.0 * resistanceScale;
        double bonus = CombatRules.KNOCKBACK_BONUS_BPS / 10.0 * knockbackLevel * resistanceScale;
        boolean grounded = mob.onGround;
        double previousVy = mob.vy;
        mob.applyKnockback(forwardX * base, forwardZ * base, grounded, forwardX * bonus, forwardZ * bonus);
        if (grounded && resistanceScale != 1.0) {
            double vertical = CombatRules.KNOCKBACK_VERTICAL_BPS / 10.0 * resistanceScale;
            mob.vy = Math.min(vertical, previousVy / 2.0 + vertical);
        }
        return 0;
    }

    /** 선분(0..1)이 상자에 처음 닿는 비율. 시작점이 상자 안이면 0, 닿지 않으면 -1. */
    static double segmentBoxT(double x0, double y0, double z0, double x1, double y1, double z1,
            double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        double t0 = 0.0;
        double t1 = 1.0;
        double[] origin = {x0, y0, z0};
        double[] delta = {x1 - x0, y1 - y0, z1 - z0};
        double[] lo = {minX, minY, minZ};
        double[] hi = {maxX, maxY, maxZ};
        for (int axis = 0; axis < 3; axis++) {
            if (Math.abs(delta[axis]) < 1e-12) {
                if (origin[axis] < lo[axis] || origin[axis] > hi[axis]) return -1.0;
                continue;
            }
            double a = (lo[axis] - origin[axis]) / delta[axis];
            double b = (hi[axis] - origin[axis]) / delta[axis];
            if (a > b) { double swap = a; a = b; b = swap; }
            t0 = Math.max(t0, a);
            t1 = Math.min(t1, b);
            if (t0 > t1) return -1.0;
        }
        return t0;
    }

    void emitHurt(AttackResult result) {
        if (result.accepted()) {
            broadcast.accept(new WsMessages.MobHurt(result.mobId(), result.lethal()));
        }
    }

    static final class AttackResult {
        private final boolean accepted;
        private final boolean inventoryChanged;
        private final long mobId;
        private final boolean critical;
        private final boolean enchanted;
        private final boolean lethal;
        private final double x;
        private final double y;
        private final double z;
        private final boolean sweep;
        private final MaceSmash maceSmash;

        private AttackResult(boolean accepted, boolean inventoryChanged, long mobId,
                boolean critical, boolean enchanted, boolean lethal, double x, double y, double z) {
            this(accepted, inventoryChanged, mobId, critical, enchanted, lethal, x, y, z, false);
        }

        private AttackResult(boolean accepted, boolean inventoryChanged, long mobId,
                boolean critical, boolean enchanted, boolean lethal, double x, double y, double z,
                boolean sweep) {
            this(accepted, inventoryChanged, mobId, critical, enchanted, lethal, x, y, z, sweep, null);
        }

        private AttackResult(boolean accepted, boolean inventoryChanged, long mobId,
                boolean critical, boolean enchanted, boolean lethal, double x, double y, double z,
                boolean sweep, MaceSmash maceSmash) {
            this.maceSmash = maceSmash;
            this.sweep = sweep;
            this.accepted = accepted;
            this.inventoryChanged = inventoryChanged;
            this.mobId = mobId;
            this.critical = critical;
            this.enchanted = enchanted;
            this.lethal = lethal;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        static AttackResult rejected() {
            return new AttackResult(false, false, 0, false, false, false, 0, 0, 0);
        }
        boolean accepted() { return accepted; }
        boolean inventoryChanged() { return inventoryChanged; }
        long mobId() { return mobId; }
        boolean critical() { return critical; }
        /** [CRIT-FX] 인챈트 보너스 피해가 실린 타격(바닐라 magicCrit) — ENCHANTED_HIT 파티클용. */
        boolean enchanted() { return enchanted; }
        boolean lethal() { return lethal; }
        double x() { return x; }
        double y() { return y; }
        double z() { return z; }
        /** [ENCHANT-WIDE] 휩쓸기 공격을 냈는가(SWEEP_ATTACK 파티클). */
        boolean sweep() { return sweep; }
        /** [MACE] 받아들여진 낙하 강타. 강타가 아니면 null. */
        MaceSmash maceSmash() { return maceSmash; }
    }

    /**
     * 역사 버전의 복잡한 연쇄 경보 타이머는 미확인이라 넣지 않는다. 최초 피격 위치에서
     * 확인된 32블록 안 동종을 같은 공격자에게 즉시 적대시키는 WebCraft 방 단위 매핑이다.
     */
    private void angerNearbyPigmen(Mob source, String attackerNickname) {
        double radiusSq = ZombiePigman.GROUP_ANGER_RADIUS * ZombiePigman.GROUP_ANGER_RADIUS;
        for (Mob candidate : mobs) {
            if (!(candidate instanceof ZombiePigman pigman) || candidate.isDead()) continue;
            double dx = candidate.x - source.x, dy = candidate.y - source.y, dz = candidate.z - source.z;
            if (dx * dx + dy * dy + dz * dz > radiusSq) continue;
            pigman.angerAt(attackerNickname);
            // [ROTTEN-LEATHER] 무리 연쇄로 분노한 개체도 각자 원한을 받는다 — 그 종의 기존
            // 연쇄 계약이 곧 무적대 해제 범위다(개체별 원한 규칙의 예외가 아니라 적용이다).
            pigman.rememberUndeadGrudge(attackerNickname);
        }
    }

    private void angerNearbyBees(Mob source, String attackerNickname,
                                 double attackerX, double attackerZ) {
        double radiusSq = 16.0 * 16.0;
        for (Mob candidate : mobs) {
            if (candidate.type != MobType.BEE || candidate.isDead() || candidate.removed) continue;
            double dx = candidate.x - source.x;
            double dy = candidate.y - source.y;
            double dz = candidate.z - source.z;
            if (dx * dx + dy * dy + dz * dz <= radiusSq) {
                candidate.onHurt(attackerNickname, attackerX, attackerZ);
            }
        }
    }

    /** 공격 외의 서버 권위 몹 상호작용도 같은 live mob 조회를 재사용합니다. */
    Mob findAlive(long id) {
        for (Mob m : mobs) {
            if (m.id == id && !m.removed && !m.isDead()) return m;
        }
        return null;
    }
}
