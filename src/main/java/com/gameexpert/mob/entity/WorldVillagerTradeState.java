package com.gameexpert.mob.entity;

import com.gameexpert.engine.mob.villager.VillagerTradeState;
import com.gameexpert.mob.dto.VillagerTradeSnapshot;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 주민 한 명의 durable 거래 진행도 행이다.
 *
 * <p>{@code (world_id, mob_id)} unique 제약이 곧 불변식이다 — 주민 한 명당 한 행. 오퍼 네 배열
 * (레벨·풀 인덱스·사용 횟수·수요)은 항상 같은 길이이고 순서가 곧 화면의 오퍼 순서라, 네 개의
 * 자식 테이블 대신 쉼표로 이은 한 컬럼씩으로 저장한다. 오퍼는 최대
 * {@code MAX_LEVEL × OFFERS_PER_LEVEL} = 10 줄이므로 길이가 고정 상한 안에 든다.
 *
 * <p>{@code schemaVersion} 이 이 빌드가 아는 값과 다르면 {@link #toSnapshot()} 이 null 을
 * 돌려주고 복원이 그 행을 건너뛴다. 그 주민은 직업 lane 의 직업으로 오퍼를 새로 뽑으므로
 * 마이그레이션 없이도 세이브가 깨지지 않는다.
 */
@Getter
@Entity
@Table(
        name = "world_villager_trade_states",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_world_villager_trade_state_mob",
                columnNames = { "world_id", "mob_id" }))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorldVillagerTradeState {

    /** 쉼표로 이은 오퍼 배열 한 개의 상한. 10줄 × "인덱스,"(4자) 여유까지 담는다. */
    private static final int OFFER_COLUMN_LENGTH = 128;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long worldId;

    @Column(nullable = false)
    private long mobId;

    @Column(nullable = false, length = 24)
    private String profession;

    /** {@code level} 은 여러 DB 에서 예약어라 컬럼 이름을 분리한다. */
    @Column(name = "trade_level", nullable = false)
    private int tradeLevel;

    @Column(nullable = false)
    private int tradeXp;

    @Column(nullable = false)
    private long lastRestockGameTime;

    @Column(nullable = false)
    private long lastRestockCheckDayTime;

    @Column(nullable = false)
    private int restocksToday;

    @Column(nullable = false, length = OFFER_COLUMN_LENGTH)
    private String offerLevels;

    @Column(nullable = false, length = OFFER_COLUMN_LENGTH)
    private String offerPoolIndices;

    @Column(nullable = false, length = OFFER_COLUMN_LENGTH)
    private String offerUses;

    @Column(nullable = false, length = OFFER_COLUMN_LENGTH)
    private String offerDemand;

    @Column(length = OFFER_COLUMN_LENGTH)
    private String offerVariantSeeds;

    @Column(nullable = false)
    private int schemaVersion;

    public WorldVillagerTradeState(Long worldId, VillagerTradeSnapshot snapshot) {
        this.worldId = worldId;
        this.mobId = snapshot.mobId();
        apply(snapshot);
    }

    /** 같은 주민의 새 진행도를 기존 행에 덮어쓴다. */
    public void apply(VillagerTradeSnapshot snapshot) {
        VillagerTradeState.Snapshot state = snapshot.state();
        this.profession = state.profession();
        this.tradeLevel = state.level();
        this.tradeXp = state.totalXp();
        this.lastRestockGameTime = state.lastRestockGameTime();
        this.lastRestockCheckDayTime = state.lastRestockCheckDayTime();
        this.restocksToday = state.restocksToday();
        this.offerLevels = join(state.offerLevels());
        this.offerPoolIndices = join(state.offerPoolIndices());
        this.offerUses = join(state.uses());
        this.offerDemand = join(state.demand());
        this.offerVariantSeeds = join(state.variantSeeds());
        this.schemaVersion = snapshot.schemaVersion();
    }

    /** 이 빌드가 해석할 수 없는 행이면 null 이다. 복원은 그 행만 건너뛴다. */
    public VillagerTradeSnapshot toSnapshot() {
        if (schemaVersion != VillagerTradeSnapshot.SCHEMA_VERSION) return null;
        int[] levels = split(offerLevels);
        int[] indices = split(offerPoolIndices);
        int[] uses = split(offerUses);
        int[] demand = split(offerDemand);
        if (levels == null || indices == null || uses == null || demand == null) return null;
        int[] seeds = offerVariantSeeds == null ? new int[levels.length] : split(offerVariantSeeds);
        if (seeds == null) return null;
        try {
            return new VillagerTradeSnapshot(mobId, new VillagerTradeState.Snapshot(
                    profession, tradeLevel, tradeXp,
                    lastRestockGameTime, lastRestockCheckDayTime, restocksToday,
                    levels, indices, uses, demand, seeds), schemaVersion);
        } catch (IllegalArgumentException invalidRow) {
            return null;
        }
    }

    private static String join(int[] values) {
        if (values.length == 0) return "";
        StringBuilder out = new StringBuilder(values.length * 3);
        for (int index = 0; index < values.length; index++) {
            if (index > 0) out.append(',');
            out.append(values[index]);
        }
        return out.toString();
    }

    /** 해석 불가면 null. 빈 문자열은 "오퍼 없음"이라는 정상 값이다. */
    private static int[] split(String encoded) {
        if (encoded == null) return null;
        if (encoded.isEmpty()) return new int[0];
        String[] parts = encoded.split(",", -1);
        int[] values = new int[parts.length];
        for (int index = 0; index < parts.length; index++) {
            try {
                values[index] = Integer.parseInt(parts[index]);
            } catch (NumberFormatException malformed) {
                return null;
            }
        }
        return values;
    }
}
