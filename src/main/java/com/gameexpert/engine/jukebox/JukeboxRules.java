package com.gameexpert.engine.jukebox;

import com.gameexpert.engine.inventory.PlayerInventory;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * [JUKEBOX] 주크박스와 음반 22종의 순수 규칙. 정적판 twin 은 {@code client/src/world/JukeboxSongs.ts}
 * 이며 {@code StandaloneJukeboxParity.test.ts} 가 이 파일의 표를 글자 그대로 읽어 대조한다.
 *
 * <p>근거(26.3-snapshot-7 jar):
 * <ul>
 *   <li>{@code data/minecraft/jukebox_song/*.json} — 곡마다 {@code sound_event},
 *       {@code length_in_seconds}, {@code comparator_output}, 설명 번역 키.</li>
 *   <li>{@code JukeboxSong.lengthInTicks()} = {@code Mth.ceil(lengthInSeconds * 20)},
 *       {@code hasFinished(t)} = {@code t >= lengthInTicks() + 20}.</li>
 *   <li>{@code JukeboxSongPlayer.PLAY_EVENT_INTERVAL_TICKS = 20}: {@code tick} 은 곡이 끝났으면
 *       {@code stop}, 아니면 {@code ticksSinceSongStarted % 20 == 0} 일 때 {@code JUKEBOX_PLAY}
 *       게임 이벤트와 음표 파티클({@code spawnMusicParticles}: 블록 바닥 중앙 +1.2, 색
 *       {@code nextInt(4) / 24}) 을 내고, 끝으로 틱을 1 올린다.</li>
 *   <li>{@code JukeboxBlockEntity.getComparatorOutput()} = 들어 있는 음반 곡의
 *       {@code comparator_output} (재생 중인지와 무관, 빈 주크박스 0).</li>
 *   <li>{@code JukeboxBlock.isSignalSource() = true}, {@code ownSignal} = 재생 중이면 15, 아니면 0.</li>
 *   <li>{@code JukeboxBlockEntity.canPlaceItem}: {@code JUKEBOX_PLAYABLE} 이 있고 칸이 비었을 때,
 *       {@code canTakeItem(target, …)}: 받는 쪽 컨테이너에 빈 칸이 하나라도 있을 때
 *       ({@code target.hasAnyMatching(ItemStack::isEmpty)}), {@code getMaxStackSize() = 1}.</li>
 *   <li>{@code JukeboxBlockEntity.popOutTheItem}: 블록 하단 모서리 + (0.5, 1.01, 0.5) 에서
 *       XZ 로 ±0.7 흩어 떨군다. 빈손 사용({@code useWithoutItem})·파괴({@code preRemoveSideEffects})
 *       모두 이 경로다.</li>
 *   <li>{@code JukeboxBlockEntity.loadAdditional}: {@code RecordItem} 과
 *       {@code ticks_since_song_started} 를 읽고, 곡이 아직 끝나지 않았으면
 *       {@code setSongWithoutPlaying} 으로 이어서 재생한다. {@code saveAdditional} 은 곡이 재생
 *       중일 때만 틱을 쓴다.</li>
 * </ul>
 *
 * <p>권위 틱은 10 TPS 라 한 서버 틱에 MC 틱 {@link #MC_TICKS_PER_SERVER_TICK} 을 더한다. 0 에서 2 씩
 * 오르므로 20 의 배수 판정은 바닐라와 같은 MC 틱에서 맞는다.
 */
public final class JukeboxRules {

    /** 10 TPS 권위 틱 한 번 = 바닐라 20 TPS 두 틱. */
    public static final int MC_TICKS_PER_SERVER_TICK = 2;
    /** {@code JukeboxSongPlayer.PLAY_EVENT_INTERVAL_TICKS}. */
    public static final int PLAY_EVENT_INTERVAL_TICKS = 20;
    /** {@code JukeboxSong.hasFinished}: 곡 길이 뒤에 붙는 여유 틱. */
    public static final int FINISH_PADDING_TICKS = 20;
    /** {@code JukeboxBlock.ownSignal} 재생 중 값. */
    public static final int PLAYING_SIGNAL = 15;
    /** {@code BlockStateProperties.HAS_RECORD} 비트(블록 상태 0/1). */
    public static final int HAS_RECORD = 1;
    /** {@code popOutTheItem} 의 {@code Vec3.atLowerCornerWithOffset(pos, 0.5, 1.01, 0.5)}. */
    public static final double POP_OUT_Y = 1.01;
    /** {@code popOutTheItem} 의 {@code offsetRandomXZ(random, 0.7F)}. */
    public static final double POP_OUT_SPREAD = 0.7;
    // 음표 파티클(spawnMusicParticles, 블록 바닥 중앙 +1.2)은 클라이언트 표현이라 권위에 상수가 없다
    // (정적판 · 클라 공용 JUKEBOX_NOTE_PARTICLE_Y).

    /** 곡 하나. {@code key} 는 {@code jukebox_song/<key>.json} 의 경로 이름이다. */
    public static final class Song {
        private final String key;
        private final short disc;
        private final int comparatorOutput;
        private final double lengthInSeconds;
        private final String description;

        public Song(String key, short disc, int comparatorOutput, double lengthInSeconds,
                String description) {
            this.key = key;
            this.disc = disc;
            this.comparatorOutput = comparatorOutput;
            this.lengthInSeconds = lengthInSeconds;
            this.description = description;
        }

        public String key() { return key; }
        public short disc() { return disc; }
        public int comparatorOutput() { return comparatorOutput; }
        public double lengthInSeconds() { return lengthInSeconds; }
        public String description() { return description; }

        /** {@code JukeboxSong.lengthInTicks()}. */
        public int lengthInTicks() {
            return (int) Math.ceil((float) lengthInSeconds * 20.0f);
        }

        /** {@code JukeboxSong.hasFinished(ticksSinceSongStarted)}. */
        public boolean hasFinished(long ticksSinceSongStarted) {
            return ticksSinceSongStarted >= lengthInTicks() + FINISH_PADDING_TICKS;
        }

        /** 바닐라 사운드 이벤트 {@code minecraft:music_disc.<key>}. */
        public String soundEvent() {
            return "minecraft:music_disc." + key;
        }
    }

    /**
     * 26.3 {@code jukebox_song} 22 곡. 순서는 jar 의 파일 이름 정렬 순이다(프로토콜은 순번이 아니라
     * {@code key} 문자열을 싣는다). 설명은 {@code lang/en_us.json} 의 {@code jukebox_song.minecraft.*}.
     */
    public static final List<Song> SONGS = List.of(
            new Song("11", PlayerInventory.MUSIC_DISC_11, 11, 71.0, "C418 - 11"),
            new Song("13", PlayerInventory.MUSIC_DISC_13, 1, 178.0, "C418 - 13"),
            new Song("5", PlayerInventory.MUSIC_DISC_5, 15, 178.0, "Samuel Åberg - 5"),
            new Song("blocks", PlayerInventory.MUSIC_DISC_BLOCKS, 3, 345.0, "C418 - blocks"),
            new Song("bounce", PlayerInventory.MUSIC_DISC_BOUNCE, 8, 234.0, "fingerspit - Bounce"),
            new Song("cat", PlayerInventory.MUSIC_DISC_CAT, 2, 185.0, "C418 - cat"),
            new Song("chirp", PlayerInventory.MUSIC_DISC_CHIRP, 4, 185.0, "C418 - chirp"),
            new Song("creator", PlayerInventory.MUSIC_DISC_CREATOR, 12, 176.0,
                    "Lena Raine - Creator"),
            new Song("creator_music_box", PlayerInventory.MUSIC_DISC_CREATOR_MUSIC_BOX, 11, 73.0,
                    "Lena Raine - Creator (Music Box)"),
            new Song("far", PlayerInventory.MUSIC_DISC_FAR, 5, 174.0, "C418 - far"),
            new Song("lava_chicken", PlayerInventory.MUSIC_DISC_LAVA_CHICKEN, 9, 134.0,
                    "Hyper Potions - Lava Chicken"),
            new Song("mall", PlayerInventory.MUSIC_DISC_MALL, 6, 197.0, "C418 - mall"),
            new Song("mellohi", PlayerInventory.MUSIC_DISC_MELLOHI, 7, 96.0, "C418 - mellohi"),
            new Song("otherside", PlayerInventory.MUSIC_DISC_OTHERSIDE, 14, 195.0,
                    "Lena Raine - otherside"),
            new Song("pigstep", PlayerInventory.MUSIC_DISC_PIGSTEP, 13, 149.0,
                    "Lena Raine - Pigstep"),
            new Song("precipice", PlayerInventory.MUSIC_DISC_PRECIPICE, 13, 299.0,
                    "Aaron Cherof - Precipice"),
            new Song("relic", PlayerInventory.MUSIC_DISC_RELIC, 14, 218.0, "Aaron Cherof - Relic"),
            new Song("stal", PlayerInventory.MUSIC_DISC_STAL, 8, 150.0, "C418 - stal"),
            new Song("strad", PlayerInventory.MUSIC_DISC_STRAD, 9, 188.0, "C418 - strad"),
            new Song("tears", PlayerInventory.MUSIC_DISC_TEARS, 10, 175.0, "Amos Roddy - Tears"),
            new Song("wait", PlayerInventory.MUSIC_DISC_WAIT, 12, 238.0, "C418 - wait"),
            new Song("ward", PlayerInventory.MUSIC_DISC_WARD, 10, 251.0, "C418 - ward"));

    /**
     * {@code #minecraft:creeper_drop_music_discs} (26.3 {@code tags/item/creeper_drop_music_discs.json})
     * 의 원문 순서. {@code entities/creeper.json} 둘째 pool 의 {@code tag}(expand) 항목은 이 순서의
     * 동일 가중치(1) 12 개로 펼쳐진다.
     */
    public static final List<Short> CREEPER_DROP_MUSIC_DISCS = List.of(
            PlayerInventory.MUSIC_DISC_13, PlayerInventory.MUSIC_DISC_CAT,
            PlayerInventory.MUSIC_DISC_BLOCKS, PlayerInventory.MUSIC_DISC_CHIRP,
            PlayerInventory.MUSIC_DISC_FAR, PlayerInventory.MUSIC_DISC_MALL,
            PlayerInventory.MUSIC_DISC_MELLOHI, PlayerInventory.MUSIC_DISC_STAL,
            PlayerInventory.MUSIC_DISC_STRAD, PlayerInventory.MUSIC_DISC_WARD,
            PlayerInventory.MUSIC_DISC_11, PlayerInventory.MUSIC_DISC_WAIT);

    private static final Map<Short, Song> BY_DISC = SONGS.stream()
            .collect(Collectors.toUnmodifiableMap(Song::disc, Function.identity()));
    private static final Map<String, Song> BY_KEY = SONGS.stream()
            .collect(Collectors.toUnmodifiableMap(Song::key, Function.identity()));

    private JukeboxRules() {}

    /** 이 아이템에 {@code JUKEBOX_PLAYABLE} 성분이 있는가(음반 22종). */
    public static boolean isMusicDisc(short itemType) {
        return BY_DISC.containsKey(itemType);
    }

    /** 음반의 곡. 음반이 아니면 null. */
    public static Song songFor(short itemType) {
        return BY_DISC.get(itemType);
    }

    /** {@code key} 의 곡. 없으면 null. */
    public static Song songByKey(String key) {
        return key == null ? null : BY_KEY.get(key);
    }

    /** {@code JukeboxBlockEntity.getComparatorOutput()}: 빈 주크박스 0. */
    public static int comparatorOutput(short discOrEmpty) {
        Song song = songFor(discOrEmpty);
        return song == null ? 0 : song.comparatorOutput();
    }

    /** {@code JukeboxBlock.ownSignal}: 재생 중이면 15. */
    public static int ownSignal(boolean playing) {
        return playing ? PLAYING_SIGNAL : 0;
    }

    /** {@code JukeboxSongPlayer.shouldEmitJukeboxPlayingEvent}: 20 틱마다 한 번. */
    public static boolean emitsPlayingEvent(long ticksSinceSongStarted) {
        return ticksSinceSongStarted % PLAY_EVENT_INTERVAL_TICKS == 0;
    }

    /**
     * 한 권위 틱(= MC 두 틱) 동안의 {@code JukeboxSongPlayer.tick} 을 그대로 두 번 돈다. 반환값은
     * 새 {@code ticksSinceSongStarted} 이며, 두 틱 안에서 곡이 끝났으면 -1 이다. 재생 이벤트(파티클)
     * 는 {@code events[0]} 에 이 틱 안에서 20 의 배수를 지난 횟수로 싣는다.
     */
    public static long advance(Song song, long ticksSinceSongStarted, int[] events) {
        long ticks = ticksSinceSongStarted;
        int emitted = 0;
        for (int step = 0; step < MC_TICKS_PER_SERVER_TICK; step++) {
            if (song.hasFinished(ticks)) {
                if (events != null) events[0] = emitted;
                return -1L;
            }
            if (emitsPlayingEvent(ticks)) emitted++;
            ticks++;
        }
        if (events != null) events[0] = emitted;
        return ticks;
    }

    // ── 블록 상태 ─────────────────────────────────────────────────────────────────
    // 바닐라처럼 음반 ItemStack(모든 성분)과 곡 시계는 블록 엔티티에 산다(world_jukeboxes 행,
    // 정적판 StandaloneJukeboxState). 블록 state 는 바닐라 HAS_RECORD 한 비트뿐이다.

    /**
     * 재생 중인 곡 시계({@code ticks_since_song_started})를 행으로 영속하는 주기(권위 틱, 10 초).
     * 바닐라는 청크 저장 때 적는다. 이 저장소는 이 주기 · 곡 끝 · 음반 교체 때 적어, 재시작 뒤 최대
     * 10 초 안의 오차로 곡이 이어진다(정적판 {@code JUKEBOX_CHECKPOINT_AUTHORITY_TICKS} 와 같은 값).
     */
    public static final int CHECKPOINT_SERVER_TICKS = 100;

    /** 음반 유무의 주크박스 state. */
    public static int stateFor(boolean hasRecord) {
        return hasRecord ? HAS_RECORD : 0;
    }

    /** 저장·방송 전의 정규화: HAS_RECORD 비트만 남긴다. */
    public static int normalizeState(int state) {
        return state & HAS_RECORD;
    }

    /**
     * 주크박스에 넣을 수 있는 스택({@code JukeboxBlockEntity.canPlaceItem} · {@code JukeboxPlayable}):
     * 음반이면 이름 · 인챈트 등 다른 성분이 붙어 있어도 된다. 한 장이 성분째 블록 엔티티에 들어간다.
     */
    public static boolean canInsert(PlayerInventory.StackSnapshot stack) {
        return stack != null && stack.count() > 0 && isMusicDisc(stack.itemType());
    }

    /** {@code spawnMusicParticles} 의 음표 색 {@code nextInt(4) / 24F}. */
    public static float noteColor(int roll) {
        if (roll < 0 || roll > 3) throw new IllegalArgumentException("note roll outside nextInt(4)");
        return roll / 24.0f;
    }
}
