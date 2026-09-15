package com.gameexpert.engine.effect;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.gameexpert.engine.inventory.PlayerInventory;
import com.gameexpert.engine.mob.ProjectileEffect;

/**
 * [BREWING-26.3] 바닐라 26.3-snapshot-7 물약 등록부의 단일 정본(순수 함수).
 *
 * <p>물약 한 병의 정체성은 <b>(형태, 물약 키)</b> 한 쌍이다. 형태는 바닐라 아이템
 * {@code minecraft:potion} · {@code splash_potion} · {@code lingering_potion} 셋이고, 물약 키는
 * {@code minecraft:potion_contents} 컴포넌트의 {@code potion} 경로(46 종, 네임스페이스 없음)다.
 *
 * <p><b>저장 호환 규약.</b> 이 저장소는 예전부터 일부 쌍에 전용 아이템 ID 61 개(물병 341 ·
 * 어색한 물약 803 · 803~832 · 1463~1466 · 1760~1779 · 2309 · 2314~2321)를 두었다. 그 ID 들은 자기가
 * 나타내던 쌍의 <b>정본으로 남는다</b>(옮기거나 번호를 바꾸지 않는다). 나머지 쌍은 전부 범용
 * 아이템 {@code CONTENTS_POTION} · {@code CONTENTS_SPLASH_POTION} · {@code CONTENTS_LINGERING_POTION}
 * 에 {@code potionContents} 스택 컴포넌트를 실어 나타낸다. {@link #canonicalItemType} ·
 * {@link #canonicalPotionContents} 가 그 규약의 유일한 소유자이고, 범용 아이템에 전용 ID 가 있는
 * 쌍이 실려 오면 {@link #resolve} 는 받아 주되 산출은 언제나 전용 ID 로 정규화한다.
 *
 * <p>근거(javap {@code gameexpert-26.3-snapshot-7-inner.jar}):
 * <ul>
 *   <li>{@code Potions.<clinit>}: 46 개 등록 순서 · 효과 · 지속(MC 틱) · 증폭(아래 표 그대로).</li>
 *   <li>{@code MobEffects.<clinit>}: 효과 색(RGB) — {@link #effectColor}.</li>
 *   <li>{@code PotionContents.getColorOptional}: 보이는 효과마다 가중치 {@code amp + 1} 로 RGB 를
 *       정수 평균, 효과가 없으면 {@code BASE_POTION_COLOR} -13083194(0xFF385DC6).</li>
 *   <li>{@code Items.<clinit>}: {@code lingering_potion} 의 {@code potion_duration_scale} 0.25 ·
 *       {@code splash_potion} 은 컴포넌트가 없어 1.0 · 둘 다 {@code stacksTo(1)}.</li>
 *   <li>{@code ThrownSplashPotion.onHitAsPotion}: 지속 = {@code (int)(근접도 × 지속 × 배율 + 0.5)},
 *       20 MC 틱 안에 끝나면 걸지 않는다({@code endsWithin(20)}).</li>
 * </ul>
 *
 * <p>지속은 전부 MC 틱(20 TPS)으로 적고, 서버 틱(10 TPS)으로 옮길 때만 2 로 나눈다(표의 값은
 * 전부 짝수라 반올림이 없다 — {@link PotionRules} 와 같은 환산 규약). 정적판
 * {@code client/src/world/potionCatalog.ts} 가 같은 표를 갖고
 * {@code StandalonePotionParity.test.ts} 가 두 표를 대조한다.
 */
public final class PotionCatalog {

    private PotionCatalog() {
    }

    /** 바닐라 물약 아이템 세 형태. */
    public enum Form {
        POTION("potion"),
        SPLASH("splash_potion"),
        LINGERING("lingering_potion");

        private final String itemKey;

        Form(String itemKey) {
            this.itemKey = itemKey;
        }

        /** 바닐라 아이템 경로({@code minecraft:} 없음). */
        public String itemKey() {
            return itemKey;
        }

        public static Form byItemKey(String key) {
            for (Form form : values()) if (form.itemKey.equals(key)) return form;
            return null;
        }
    }

    /** 물약 한 종이 싣는 효과 한 건. 지속은 MC 틱이다(즉발 효과는 바닐라와 같이 1). */
    public static final class EffectSpec {
        private final StatusEffect effect;
        private final int amplifier;
        private final int mcDurationTicks;

        public EffectSpec(StatusEffect effect, int amplifier, int mcDurationTicks) {
            if (effect == null || amplifier < 0 || mcDurationTicks < 1) {
                throw new IllegalArgumentException("invalid potion effect");
            }
            this.effect = effect;
            this.amplifier = amplifier;
            this.mcDurationTicks = mcDurationTicks;
        }

        public StatusEffect effect() { return effect; }
        public int amplifier() { return amplifier; }
        public int mcDurationTicks() { return mcDurationTicks; }

        @Override
        public boolean equals(Object other) {
            return other instanceof EffectSpec spec && spec.effect == effect
                    && spec.amplifier == amplifier && spec.mcDurationTicks == mcDurationTicks;
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(effect, amplifier, mcDurationTicks);
        }

        @Override
        public String toString() {
            return "EffectSpec[" + effect + ", " + amplifier + ", " + mcDurationTicks + "]";
        }
    }

    /**
     * 물약 한 종. {@code name} 은 바닐라 {@code Potion} 생성자의 이름 인자(연장·강화형도 기본형
     * 이름을 공유한다 — {@code item.minecraft.potion.effect.<name>}).
     */
    public static final class Potion {
        private final String key;
        private final String name;
        private final List<EffectSpec> effects;

        public Potion(String key, String name, List<EffectSpec> effects) {
            this.key = key;
            this.name = name;
            this.effects = List.copyOf(effects);
        }

        public String key() { return key; }
        public String name() { return name; }
        public List<EffectSpec> effects() { return effects; }

        @Override
        public boolean equals(Object other) {
            return other instanceof Potion potion && potion.key.equals(key) && potion.name.equals(name)
                    && potion.effects.equals(effects);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(key, name, effects);
        }

        @Override
        public String toString() {
            return "Potion[" + key + "]";
        }

        /** 바닐라 {@code Potion.hasInstantEffects}. */
        public boolean hasInstantEffects() {
            for (EffectSpec effect : effects) if (effect.effect().instantaneous()) return true;
            return false;
        }
    }

    /** (형태, 물약 키) 한 쌍. */
    public static final class Contents {
        private final Form form;
        private final String key;

        public Contents(Form form, String key) {
            if (form == null || !isKey(key)) {
                throw new IllegalArgumentException("invalid potion contents " + form + ":" + key);
            }
            this.form = form;
            this.key = key;
        }

        public Form form() { return form; }
        public String key() { return key; }

        @Override
        public boolean equals(Object other) {
            return other instanceof Contents contents && contents.form == form && contents.key.equals(key);
        }

        @Override
        public int hashCode() {
            return 31 * form.hashCode() + key.hashCode();
        }

        @Override
        public String toString() {
            return "Contents[" + form + ":" + key + "]";
        }
    }

    /** 바닐라 {@code PotionContents.BASE_POTION_COLOR} 의 RGB(0xFF385DC6). */
    public static final int BASE_POTION_COLOR = 0x385DC6;
    /** {@code Items.LINGERING_POTION} 의 {@code potion_duration_scale}. */
    public static final float LINGERING_DURATION_SCALE = 0.25f;
    /** {@code ThrownSplashPotion}: 20 MC 틱 안에 끝나는 효과는 걸지 않는다. */
    public static final int SPLASH_MIN_MC_TICKS = 20;

    private static final Map<String, Potion> BY_KEY = new LinkedHashMap<>();
    private static final Map<Short, Contents> DEDICATED_BY_ITEM = new HashMap<>();
    private static final Map<Contents, Short> DEDICATED_BY_CONTENTS = new HashMap<>();

    private static void potion(String key, String name, EffectSpec... effects) {
        if (BY_KEY.put(key, new Potion(key, name, List.of(effects))) != null) {
            throw new IllegalStateException("duplicate potion " + key);
        }
    }

    private static EffectSpec e(StatusEffect effect, int mcTicks) {
        return new EffectSpec(effect, 0, mcTicks);
    }

    private static EffectSpec e(StatusEffect effect, int mcTicks, int amplifier) {
        return new EffectSpec(effect, amplifier, mcTicks);
    }

    private static void dedicated(short itemType, Form form, String key) {
        Contents contents = new Contents(form, key);
        if (DEDICATED_BY_ITEM.put(itemType, contents) != null
                || DEDICATED_BY_CONTENTS.put(contents, itemType) != null) {
            throw new IllegalStateException("duplicate dedicated potion " + itemType);
        }
    }

    static {
        // ── Potions.<clinit> 등록 순서 그대로(46 종) ──
        potion("water", "water");
        potion("mundane", "mundane");
        potion("thick", "thick");
        potion("awkward", "awkward");
        potion("night_vision", "night_vision", e(StatusEffect.NIGHT_VISION, 3600));
        potion("long_night_vision", "night_vision", e(StatusEffect.NIGHT_VISION, 9600));
        potion("invisibility", "invisibility", e(StatusEffect.INVISIBILITY, 3600));
        potion("long_invisibility", "invisibility", e(StatusEffect.INVISIBILITY, 9600));
        potion("leaping", "leaping", e(StatusEffect.JUMP_BOOST, 3600));
        potion("long_leaping", "leaping", e(StatusEffect.JUMP_BOOST, 9600));
        potion("strong_leaping", "leaping", e(StatusEffect.JUMP_BOOST, 1800, 1));
        potion("fire_resistance", "fire_resistance", e(StatusEffect.FIRE_RESISTANCE, 3600));
        potion("long_fire_resistance", "fire_resistance", e(StatusEffect.FIRE_RESISTANCE, 9600));
        potion("swiftness", "swiftness", e(StatusEffect.SPEED, 3600));
        potion("long_swiftness", "swiftness", e(StatusEffect.SPEED, 9600));
        potion("strong_swiftness", "swiftness", e(StatusEffect.SPEED, 1800, 1));
        potion("slowness", "slowness", e(StatusEffect.SLOWNESS, 1800));
        potion("long_slowness", "slowness", e(StatusEffect.SLOWNESS, 4800));
        potion("strong_slowness", "slowness", e(StatusEffect.SLOWNESS, 400, 3));
        potion("turtle_master", "turtle_master",
                e(StatusEffect.SLOWNESS, 400, 3), e(StatusEffect.RESISTANCE, 400, 2));
        potion("long_turtle_master", "turtle_master",
                e(StatusEffect.SLOWNESS, 800, 3), e(StatusEffect.RESISTANCE, 800, 2));
        potion("strong_turtle_master", "turtle_master",
                e(StatusEffect.SLOWNESS, 400, 5), e(StatusEffect.RESISTANCE, 400, 3));
        potion("water_breathing", "water_breathing", e(StatusEffect.WATER_BREATHING, 3600));
        potion("long_water_breathing", "water_breathing", e(StatusEffect.WATER_BREATHING, 9600));
        potion("healing", "healing", e(StatusEffect.INSTANT_HEALTH, 1));
        potion("strong_healing", "healing", e(StatusEffect.INSTANT_HEALTH, 1, 1));
        potion("harming", "harming", e(StatusEffect.INSTANT_DAMAGE, 1));
        potion("strong_harming", "harming", e(StatusEffect.INSTANT_DAMAGE, 1, 1));
        potion("poison", "poison", e(StatusEffect.POISON, 900));
        potion("long_poison", "poison", e(StatusEffect.POISON, 1800));
        potion("strong_poison", "poison", e(StatusEffect.POISON, 432, 1));
        potion("regeneration", "regeneration", e(StatusEffect.REGENERATION, 900));
        potion("long_regeneration", "regeneration", e(StatusEffect.REGENERATION, 1800));
        potion("strong_regeneration", "regeneration", e(StatusEffect.REGENERATION, 450, 1));
        potion("strength", "strength", e(StatusEffect.STRENGTH, 3600));
        potion("long_strength", "strength", e(StatusEffect.STRENGTH, 9600));
        potion("strong_strength", "strength", e(StatusEffect.STRENGTH, 1800, 1));
        potion("weakness", "weakness", e(StatusEffect.WEAKNESS, 1800));
        potion("long_weakness", "weakness", e(StatusEffect.WEAKNESS, 4800));
        potion("luck", "luck", e(StatusEffect.LUCK, 6000));
        potion("slow_falling", "slow_falling", e(StatusEffect.SLOW_FALLING, 1800));
        potion("long_slow_falling", "slow_falling", e(StatusEffect.SLOW_FALLING, 4800));
        potion("wind_charged", "wind_charged", e(StatusEffect.WIND_CHARGED, 3600));
        potion("weaving", "weaving", e(StatusEffect.WEAVING, 3600));
        potion("oozing", "oozing", e(StatusEffect.OOZING, 3600));
        potion("infested", "infested", e(StatusEffect.INFESTED, 3600));

        // ── 전용 ID 61 개(저장 호환 정본). 번호는 절대 옮기지 않는다. ──
        dedicated(PlayerInventory.WATER_BOTTLE, Form.POTION, "water");
        dedicated(PlayerInventory.AWKWARD_POTION, Form.POTION, "awkward");
        dedicated(PlayerInventory.POTION_SWIFTNESS, Form.POTION, "swiftness");
        dedicated(PlayerInventory.POTION_POISON, Form.POTION, "poison");
        dedicated(PlayerInventory.POTION_SLOWNESS, Form.POTION, "slowness");
        dedicated(PlayerInventory.POTION_WEAKNESS, Form.POTION, "weakness");
        dedicated(PlayerInventory.POTION_HARMING, Form.POTION, "harming");
        dedicated(PlayerInventory.SPLASH_POTION_SWIFTNESS, Form.SPLASH, "swiftness");
        dedicated(PlayerInventory.SPLASH_POTION_POISON, Form.SPLASH, "poison");
        dedicated(PlayerInventory.SPLASH_POTION_SLOWNESS, Form.SPLASH, "slowness");
        dedicated(PlayerInventory.SPLASH_POTION_WEAKNESS, Form.SPLASH, "weakness");
        dedicated(PlayerInventory.SPLASH_POTION_HARMING, Form.SPLASH, "harming");
        dedicated(PlayerInventory.POTION_SWIFTNESS_LONG, Form.POTION, "long_swiftness");
        dedicated(PlayerInventory.POTION_POISON_LONG, Form.POTION, "long_poison");
        dedicated(PlayerInventory.POTION_SLOWNESS_LONG, Form.POTION, "long_slowness");
        dedicated(PlayerInventory.POTION_WEAKNESS_LONG, Form.POTION, "long_weakness");
        dedicated(PlayerInventory.POTION_SWIFTNESS_II, Form.POTION, "strong_swiftness");
        dedicated(PlayerInventory.POTION_POISON_II, Form.POTION, "strong_poison");
        dedicated(PlayerInventory.POTION_SLOWNESS_II, Form.POTION, "strong_slowness");
        dedicated(PlayerInventory.POTION_HARMING_II, Form.POTION, "strong_harming");
        dedicated(PlayerInventory.SPLASH_POTION_SWIFTNESS_LONG, Form.SPLASH, "long_swiftness");
        dedicated(PlayerInventory.SPLASH_POTION_POISON_LONG, Form.SPLASH, "long_poison");
        dedicated(PlayerInventory.SPLASH_POTION_SLOWNESS_LONG, Form.SPLASH, "long_slowness");
        dedicated(PlayerInventory.SPLASH_POTION_WEAKNESS_LONG, Form.SPLASH, "long_weakness");
        dedicated(PlayerInventory.SPLASH_POTION_SWIFTNESS_II, Form.SPLASH, "strong_swiftness");
        dedicated(PlayerInventory.SPLASH_POTION_POISON_II, Form.SPLASH, "strong_poison");
        dedicated(PlayerInventory.SPLASH_POTION_SLOWNESS_II, Form.SPLASH, "strong_slowness");
        dedicated(PlayerInventory.SPLASH_POTION_HARMING_II, Form.SPLASH, "strong_harming");
        dedicated(PlayerInventory.POTION_FIRE_RESISTANCE, Form.POTION, "fire_resistance");
        dedicated(PlayerInventory.POTION_FIRE_RESISTANCE_LONG, Form.POTION, "long_fire_resistance");
        dedicated(PlayerInventory.SPLASH_POTION_FIRE_RESISTANCE, Form.SPLASH, "fire_resistance");
        dedicated(PlayerInventory.SPLASH_POTION_FIRE_RESISTANCE_LONG, Form.SPLASH,
                "long_fire_resistance");
        dedicated(PlayerInventory.POTION_STRENGTH, Form.POTION, "strength");
        dedicated(PlayerInventory.POTION_STRENGTH_LONG, Form.POTION, "long_strength");
        dedicated(PlayerInventory.POTION_STRENGTH_II, Form.POTION, "strong_strength");
        dedicated(PlayerInventory.SPLASH_POTION_STRENGTH, Form.SPLASH, "strength");
        dedicated(PlayerInventory.SPLASH_POTION_STRENGTH_LONG, Form.SPLASH, "long_strength");
        dedicated(PlayerInventory.SPLASH_POTION_STRENGTH_II, Form.SPLASH, "strong_strength");
        dedicated(PlayerInventory.POTION_WATER_BREATHING, Form.POTION, "water_breathing");
        dedicated(PlayerInventory.POTION_WATER_BREATHING_LONG, Form.POTION, "long_water_breathing");
        dedicated(PlayerInventory.SPLASH_POTION_WATER_BREATHING, Form.SPLASH, "water_breathing");
        dedicated(PlayerInventory.SPLASH_POTION_WATER_BREATHING_LONG, Form.SPLASH,
                "long_water_breathing");
        dedicated(PlayerInventory.POTION_LEAPING, Form.POTION, "leaping");
        dedicated(PlayerInventory.POTION_LEAPING_LONG, Form.POTION, "long_leaping");
        dedicated(PlayerInventory.POTION_LEAPING_II, Form.POTION, "strong_leaping");
        dedicated(PlayerInventory.SPLASH_POTION_LEAPING, Form.SPLASH, "leaping");
        dedicated(PlayerInventory.SPLASH_POTION_LEAPING_LONG, Form.SPLASH, "long_leaping");
        dedicated(PlayerInventory.SPLASH_POTION_LEAPING_II, Form.SPLASH, "strong_leaping");
        dedicated(PlayerInventory.POTION_NIGHT_VISION, Form.POTION, "night_vision");
        dedicated(PlayerInventory.POTION_NIGHT_VISION_LONG, Form.POTION, "long_night_vision");
        dedicated(PlayerInventory.SPLASH_POTION_NIGHT_VISION, Form.SPLASH, "night_vision");
        dedicated(PlayerInventory.SPLASH_POTION_NIGHT_VISION_LONG, Form.SPLASH,
                "long_night_vision");
        dedicated(PlayerInventory.POTION_REGENERATION, Form.POTION, "regeneration");
        // [END-CITY] 엔드 도시 전리품의 강한 즉시 회복 물약(2309).
        dedicated(PlayerInventory.POTION_STRONG_HEALING, Form.POTION, "strong_healing");
        dedicated(PlayerInventory.LINGERING_POTION_WIND_CHARGED, Form.LINGERING, "wind_charged");
        dedicated(PlayerInventory.LINGERING_POTION_OOZING, Form.LINGERING, "oozing");
        dedicated(PlayerInventory.LINGERING_POTION_WEAVING, Form.LINGERING, "weaving");
        dedicated(PlayerInventory.LINGERING_POTION_INFESTED, Form.LINGERING, "infested");
        dedicated(PlayerInventory.LINGERING_POTION_STRENGTH, Form.LINGERING, "strength");
        dedicated(PlayerInventory.LINGERING_POTION_SWIFTNESS, Form.LINGERING, "swiftness");
        dedicated(PlayerInventory.LINGERING_POTION_SLOW_FALLING, Form.LINGERING, "slow_falling");
    }

    private static final List<String> KEYS = List.copyOf(BY_KEY.keySet());

    /** 46 개 물약 키(등록 순서). */
    public static List<String> keys() {
        return KEYS;
    }

    public static boolean isKey(String key) {
        return key != null && BY_KEY.containsKey(key);
    }

    public static Potion potion(String key) {
        Potion potion = BY_KEY.get(key);
        if (potion == null) throw new IllegalArgumentException("unknown potion " + key);
        return potion;
    }

    /** 전용 ID 61 개(읽기 전용). */
    public static Map<Short, Contents> dedicatedItems() {
        return Collections.unmodifiableMap(DEDICATED_BY_ITEM);
    }

    /** 형태의 범용 아이템({@code CONTENTS_*}). */
    public static short contentsItem(Form form) {
        return switch (form) {
            case POTION -> PlayerInventory.CONTENTS_POTION;
            case SPLASH -> PlayerInventory.CONTENTS_SPLASH_POTION;
            case LINGERING -> PlayerInventory.CONTENTS_LINGERING_POTION;
        };
    }

    /** 범용 물약 아이템 세 종 중 하나인가. */
    public static boolean isContentsItem(short itemType) {
        return itemType == PlayerInventory.CONTENTS_POTION
                || itemType == PlayerInventory.CONTENTS_SPLASH_POTION
                || itemType == PlayerInventory.CONTENTS_LINGERING_POTION;
    }

    /** 범용 아이템의 형태. 범용 아이템이 아니면 null. */
    public static Form contentsForm(short itemType) {
        if (itemType == PlayerInventory.CONTENTS_POTION) return Form.POTION;
        if (itemType == PlayerInventory.CONTENTS_SPLASH_POTION) return Form.SPLASH;
        if (itemType == PlayerInventory.CONTENTS_LINGERING_POTION) return Form.LINGERING;
        return null;
    }

    /** 이 쌍의 정본 아이템: 전용 ID 가 있으면 그것, 없으면 형태의 범용 아이템. */
    public static short canonicalItemType(Form form, String key) {
        Short dedicated = DEDICATED_BY_CONTENTS.get(new Contents(form, key));
        return dedicated != null ? dedicated : contentsItem(form);
    }

    /** 정본 스택이 실어야 할 {@code potionContents}. 전용 ID 쌍이면 null(컴포넌트 없음). */
    public static String canonicalPotionContents(Form form, String key) {
        return DEDICATED_BY_CONTENTS.containsKey(new Contents(form, key)) ? null : key;
    }

    /**
     * 스택 하나를 (형태, 물약 키)로 푼다. 전용 ID 면 컴포넌트를 보지 않는다(바닐라에서 전용
     * 쌍은 컴포넌트 자체가 정체성이므로). 범용 아이템은 유효한 키가 있어야 풀린다.
     * 물약이 아니면 null.
     */
    public static Contents resolve(short itemType, String potionContents) {
        Contents dedicated = DEDICATED_BY_ITEM.get(itemType);
        if (dedicated != null) return dedicated;
        Form form = contentsForm(itemType);
        if (form == null || !isKey(potionContents)) return null;
        return new Contents(form, potionContents);
    }

    /** 물약 계열 아이템(전용 61 + 범용 3)인가. 컴포넌트 유무와 무관하다. */
    public static boolean isPotionItem(short itemType) {
        return DEDICATED_BY_ITEM.containsKey(itemType) || isContentsItem(itemType);
    }

    /** 아이템의 형태(전용·범용 공통). 물약이 아니면 null. */
    public static Form formOf(short itemType) {
        Contents dedicated = DEDICATED_BY_ITEM.get(itemType);
        return dedicated != null ? dedicated.form() : contentsForm(itemType);
    }

    // ── 색 ──

    /** {@code MobEffects.<clinit>} 의 효과 색(RGB). 표는 {@link PotionRules#effectColor} 한 곳이다. */
    public static int effectColor(StatusEffect effect) {
        return PotionRules.effectColor(effect);
    }

    /** {@code PotionContents.getColor}: 가중 정수 평균, 효과가 없으면 {@link #BASE_POTION_COLOR}. */
    public static int color(String key) {
        int red = 0, green = 0, blue = 0, total = 0;
        for (EffectSpec effect : potion(key).effects()) {
            int rgb = effectColor(effect.effect());
            int weight = effect.amplifier() + 1;
            red += weight * (rgb >> 16 & 0xFF);
            green += weight * (rgb >> 8 & 0xFF);
            blue += weight * (rgb & 0xFF);
            total += weight;
        }
        if (total == 0) return BASE_POTION_COLOR;
        return (red / total) << 16 | (green / total) << 8 | blue / total;
    }

    // ── 효과(10 TPS 서버 틱 환산) ──

    /** MC 틱 지속을 서버 틱으로 옮긴다(표의 값은 전부 짝수다 — 홀수여도 최소 1). */
    public static int serverTicks(int mcTicks) {
        return Math.max(1, mcTicks / StatusEffects.MC_TICKS_PER_SERVER_TICK);
    }

    /**
     * 마시는(배율 1.0) 효과 목록. 즉발 효과는 지속 0 의 {@link ProjectileEffect} 로 싣는다
     * (기존 즉발 경로 규약). 효과 없는 물약은 빈 목록.
     */
    public static List<ProjectileEffect> drinkEffects(String key) {
        List<ProjectileEffect> out = new ArrayList<>();
        for (EffectSpec spec : potion(key).effects()) {
            out.add(new ProjectileEffect(spec.effect(), spec.amplifier(),
                    spec.effect().instantaneous() ? 0 : serverTicks(spec.mcDurationTicks())));
        }
        return List.copyOf(out);
    }

    /**
     * 투척 물약 착탄 효과(근접도 적용 전, 배율 1.0). 바닐라 투척 물약은
     * {@code potion_duration_scale} 이 없어 마시는 지속과 같다 — 근접도는 착탄 처리가 곱한다.
     */
    public static List<ProjectileEffect> splashEffects(String key) {
        return drinkEffects(key);
    }

    /**
     * 잔류형 물약이 효과 구름에 싣는 효과(구름의 {@code potion_duration_scale} 0.25 를 곱한 값,
     * {@code MobEffectInstance.withScaledDuration}: {@code max(1, floor(지속 × 배율))}).
     */
    public static List<ProjectileEffect> lingeringEffects(String key) {
        List<ProjectileEffect> out = new ArrayList<>();
        for (EffectSpec spec : potion(key).effects()) {
            int scaled = PotionRules.scaledMcTicks(spec.mcDurationTicks(), LINGERING_DURATION_SCALE);
            out.add(new ProjectileEffect(spec.effect(), spec.amplifier(),
                    spec.effect().instantaneous() ? 0 : serverTicks(scaled)));
        }
        return List.copyOf(out);
    }

    /**
     * 투척 물약 근접도를 곱한 지속(서버 틱). 바닐라 {@code (int)(근접도 × MC 지속 + 0.5)} 를
     * MC 틱으로 계산한 뒤 서버 틱으로 옮기고, 20 MC 틱 안에 끝나면 0(걸지 않음)이다.
     */
    public static int splashScaledServerTicks(int serverDurationTicks, double proximity) {
        int mc = (int) (proximity * serverDurationTicks * StatusEffects.MC_TICKS_PER_SERVER_TICK
                + 0.5);
        if (mc <= SPLASH_MIN_MC_TICKS) return 0;
        return serverTicks(mc);
    }

    /** 바닐라 {@code HealOrHarmMobEffect}: 회복량 {@code (int)(근접도 × (4 << amp) + 0.5)}. */
    public static int instantHealAmount(int amplifier, double proximity) {
        return (int) (proximity * StatusEffects.instantHealth(amplifier) + 0.5);
    }

    /** 바닐라 {@code HealOrHarmMobEffect}: 피해량 {@code (int)(근접도 × (6 << amp) + 0.5)}. */
    public static int instantDamageAmount(int amplifier, double proximity) {
        return (int) (proximity * StatusEffects.instantDamage(amplifier) + 0.5);
    }
}
