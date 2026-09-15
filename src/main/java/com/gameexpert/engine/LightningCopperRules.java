package com.gameexpert.engine;

import java.util.SplittableRandom;
import java.util.function.IntUnaryOperator;

import com.gameexpert.terrain.Blocks;

/**
 * 낙뢰가 구리를 환원시키는 순수 규칙(바닐라 1.21.4
 * {@code LightningBolt#clearCopperOnLightningStrike} · {@code randomWalkCleaningCopper} ·
 * {@code randomStepCleaningCopper}).
 *
 * <p>바닐라 원문의 뼈대는 이렇다.
 * <pre>
 * BlockState state = level.getBlockState(pos);          // pos = bolt.blockPosition().below()
 * if (state.is(LIGHTNING_ROD)) target = pos.relative(FACING.getOpposite());
 * else                          target = pos;
 * if (level.getBlockState(target).getBlock() instanceof WeatheringCopper) {
 *     level.setBlockAndUpdate(target, WeatheringCopper.getFirst(...));   // 직격은 0단계로 한 번에
 *     int i = level.random.nextInt(3) + 3;                               // 3~5 회 걷기
 *     for (int j = 0; j &lt; i; j++) {
 *         int k = level.random.nextInt(3) + 3;                           // 걸음마다 3~5 보
 *         randomWalkCleaningCopper(level, target, cursor, k);            // 커서는 매번 target 으로 되돌린다
 *     }
 * }
 * </pre>
 * 한 보는 {@code BlockPos.randomInCube(random, 10, cursor, 1)} 의 후보 10개를 차례로 보고
 * <b>처음 만난</b> {@code WeatheringCopper} 를 {@code getPrevious}(한 단계만) 로 되돌린 뒤 그 칸으로
 * 커서를 옮긴다. 10개 안에 구리가 없으면 그 걷기는 그 자리에서 끝난다.
 *
 * <p><b>밀랍 구리는 아무 일도 없다.</b> 바닐라 {@code WaxedWeatheringCopperFullBlock} 계열은
 * {@code WeatheringCopper} 를 구현하지 않아 위 {@code instanceof} 두 곳을 모두 통과하지 못한다.
 * 밀랍이 벗겨지지도, 산화가 되돌아가지도 않는다 — 밀랍을 벗기는 것은 도끼
 * ({@link CopperAgeRules#scrapeResult(int)}) 뿐이다.
 *
 * <p>피뢰침 state는 자수정 싹과 같은 0..5 방향 어휘를 쓰며, 직격 대상은 바닐라처럼
 * {@code FACING.getOpposite()} 쪽의 부착 블록이다.
 *
 * <p><b>divergence(난수 lane)</b>: 바닐라는 {@code level.random} 을 쓰지만 WebCraft 의 낙뢰
 * 파이프라인은 "후보 결정에서만 난수를 소비한다"를 파리티 계약으로 삼는다
 * ({@link LightningStrike}). 그래서 환원 난수는 날씨 lane 을 건드리지 않고
 * <b>월드 시드 + 확정 타격 좌표</b>에서 파생한 별도 스트림({@link #deterministicRolls})으로 뽑는다.
 * 양 권위가 같은 좌표에 같은 낙뢰를 놓으면 같은 환원 결과가 나오고, 낙뢰 순서가 달라져도
 * 서로의 난수열을 오염시키지 않는다. 뽑는 <b>횟수와 경계</b>는 바닐라 그대로다.
 */
public final class LightningCopperRules {

    private LightningCopperRules() { }

    /** {@code level.random.nextInt(3) + 3} 의 하한. 걷기 횟수와 걸음 수가 같은 분포를 쓴다. */
    public static final int WALK_MIN = 3;
    /** 위 식의 {@code nextInt(3)} 폭. */
    public static final int WALK_SPREAD = 3;
    /** {@code BlockPos.randomInCube(random, 10, pos, 1)} 의 후보 개수. */
    public static final int CUBE_SAMPLES = 10;
    /** 위 {@code randomInCube} 의 반경 — 커서를 둘러싼 3×3×3. */
    public static final int CUBE_RADIUS = 1;
    /** 한 축 후보의 경우의 수({@code 2 * CUBE_RADIUS + 1}). 난수 호출의 bound 다. */
    public static final int CUBE_SPAN = CUBE_RADIUS * 2 + 1;

    /** 환원이 읽고 쓰는 블록 세계. 양 권위가 각자의 블록 경계를 여기에 끼운다. */
    public interface World {
        int blockAt(int x, int y, int z);

        /**
         * 그 칸의 blockstate. 환원은 {@code withPropertiesOf} 로 이것을 새 ID 에 그대로
         * 옮기므로 반 블록의 double, 문의 upper half 가 살아남는다.
         */
        int stateAt(int x, int y, int z);

        void setBlock(int x, int y, int z, int blockType, int blockState);
    }

    /**
     * 확정 타격점의 구리 환원. {@code strike} 는 훅 보정을 마친 <b>최종</b> 좌표이며,
     * 바닐라 {@code LightningBolt#getStrikePosition()}({@code blockPosition().below()}) 과 같이
     * 그 한 칸 <b>아래</b>가 실제로 맞은 블록이다.
     *
     * @param rolls {@code bound → [0, bound)} 난수. {@link #deterministicRolls} 가 정본이고
     *              테스트는 고정 수열을 넣는다.
     * @return 실제로 환원된 칸 수(0이면 구리가 아니었다). 회귀 테스트의 관측 지점이다.
     */
    public static int clearCopperOnLightningStrike(
            World world, LightningStrike strike, IntUnaryOperator rolls) {
        int hitX = strike.blockX();
        int hitY = strike.blockY() - 1;
        int hitZ = strike.blockZ();
        int targetX = hitX;
        int targetY = hitY;
        int targetZ = hitZ;
        if (CopperAgeRules.isLightningRod(world.blockAt(hitX, hitY, hitZ))) {
            // LightningRodBlock.FACING: 0=up, 1=down, 2=north, 3=east, 4=south, 5=west.
            // 바닐라는 rod facing의 반대편에 붙은 구리 블록으로 직격을 넘긴다.
            switch (world.stateAt(hitX, hitY, hitZ) & 7) {
                case 0 -> targetY--;
                case 1 -> targetY++;
                case 2 -> targetZ++;
                case 3 -> targetX--;
                case 4 -> targetZ--;
                case 5 -> targetX++;
                default -> targetY--; // 조작/구세이브의 예약값은 설치 기본값 UP으로 정규화한다.
            }
        }
        int first = CopperAgeRules.firstOxidationStage(world.blockAt(targetX, targetY, targetZ));
        if (first < 0) return 0;
        // 바닐라 WeatheringCopper.getFirst 도 `withPropertiesOf(state)` 로 속성을 옮긴다.
        // 0단계 구리를 직격해도 ID 는 그대로이므로 state 를 다시 써 넣기만 한다.
        world.setBlock(targetX, targetY, targetZ, first,
                world.stateAt(targetX, targetY, targetZ));
        int cleaned = 1;
        int walks = WALK_MIN + rolls.applyAsInt(WALK_SPREAD);
        for (int walk = 0; walk < walks; walk++) {
            int steps = WALK_MIN + rolls.applyAsInt(WALK_SPREAD);
            cleaned += randomWalk(world, targetX, targetY, targetZ, steps, rolls);
        }
        return cleaned;
    }

    /** {@code randomWalkCleaningCopper}: 커서를 직격 칸으로 되돌리고 {@code steps} 보 걷는다. */
    private static int randomWalk(
            World world, int startX, int startY, int startZ, int steps, IntUnaryOperator rolls) {
        int cursorX = startX;
        int cursorY = startY;
        int cursorZ = startZ;
        int cleaned = 0;
        for (int step = 0; step < steps; step++) {
            int[] moved = randomStep(world, cursorX, cursorY, cursorZ, rolls);
            if (moved == null) break;
            cursorX = moved[0];
            cursorY = moved[1];
            cursorZ = moved[2];
            cleaned += moved[3];
        }
        return cleaned;
    }

    /**
     * {@code randomStepCleaningCopper}: {@code randomInCube} 후보 10개를 순서대로 보고 처음 만난
     * 산화 구리를 한 단계 되돌린 뒤 그 칸을 새 커서로 돌려준다. 없으면 {@code null}.
     *
     * <p>이미 0단계인 구리도 <b>커서는 옮긴다</b> — 바닐라의 {@code getPrevious(...).ifPresent(...)}
     * 는 되돌릴 것이 없을 때 블록만 두고 {@code Optional.of(pos)} 로 걷기를 이어간다.
     * 난수는 후보마다 x·y·z 세 번, 후보를 채택해도 그 자리에서 멈추므로 소비량이 가변이다.
     */
    private static int[] randomStep(
            World world, int cursorX, int cursorY, int cursorZ, IntUnaryOperator rolls) {
        for (int sample = 0; sample < CUBE_SAMPLES; sample++) {
            int x = cursorX - CUBE_RADIUS + rolls.applyAsInt(CUBE_SPAN);
            int y = cursorY - CUBE_RADIUS + rolls.applyAsInt(CUBE_SPAN);
            int z = cursorZ - CUBE_RADIUS + rolls.applyAsInt(CUBE_SPAN);
            int id = world.blockAt(x, y, z);
            if (!CopperAgeRules.isOxidizable(id)) continue;
            int previous = CopperAgeRules.previousOxidationStage(id);
            if (previous >= 0) {
                // getPrevious 역시 withPropertiesOf 다 — 걷기가 밟은 반 블록/문도 형상을 잃지 않는다.
                world.setBlock(x, y, z, previous, world.stateAt(x, y, z));
                return new int[] { x, y, z, 1 };
            }
            return new int[] { x, y, z, 0 };
        }
        return null;
    }

    /**
     * 월드 시드와 확정 타격 좌표에서 파생한 환원 전용 난수 스트림. 정적판
     * {@code standaloneLightningCopperRolls} 가 같은 시드·같은 알고리즘을 쓴다
     * ({@code SplittableRandom} + {@code nextInt(origin, bound)}).
     */
    public static IntUnaryOperator deterministicRolls(int worldSeed, LightningStrike strike) {
        SplittableRandom random = new SplittableRandom(
                seedFor(worldSeed, strike.blockX(), strike.blockY(), strike.blockZ()));
        return bound -> random.nextInt(0, bound);
    }

    /** 위 스트림의 시드. 좌표 해시는 {@code MobVariant} 의 mix64 사슬과 같은 모양이다. */
    public static long seedFor(int worldSeed, int x, int y, int z) {
        long hash = mix64(worldSeed);
        hash = mix64(hash ^ x);
        hash = mix64(hash ^ y);
        hash = mix64(hash ^ z);
        return mix64(hash ^ 0x4c47484e434f5052L);
    }

    private static long mix64(long value) {
        value ^= value >>> 30;
        value *= 0xbf58476d1ce4e5b9L;
        value ^= value >>> 27;
        value *= 0x94d049bb133111ebL;
        return value ^ (value >>> 31);
    }
}
