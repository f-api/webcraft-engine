package com.gameexpert.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class DeviceNicknamesTest {

    private final DeviceNicknameRepository repository = mock(DeviceNicknameRepository.class);
    private final DeviceNicknameWriter writer = mock(DeviceNicknameWriter.class);
    private final DeviceNicknames nicknames = new DeviceNicknames(repository, writer);

    private static final String DEVICE = DeviceNicknames.hash("device-key");

    @Test
    void keepsTheNameForTheFirstDeviceThatUsesIt() {
        when(repository.findByStoredNickname(anyString())).thenReturn(Optional.empty());
        when(repository.findByDeviceHashAndDisplayNickname(anyString(), anyString())).thenReturn(Optional.empty());
        when(repository.existsByDisplayNickname("steve")).thenReturn(false);

        assertThat(nicknames.storedFor(DEVICE, "steve")).isEqualTo("steve");
        verify(writer).insert(DEVICE, "steve", "steve");
    }

    @Test
    void givesAnotherDeviceItsOwnTaggedName() {
        when(repository.findByStoredNickname(anyString())).thenReturn(Optional.empty());
        when(repository.findByDeviceHashAndDisplayNickname(anyString(), anyString())).thenReturn(Optional.empty());
        when(repository.existsByDisplayNickname("steve")).thenReturn(true);

        String stored = nicknames.storedFor(DEVICE, "steve");

        assertThat(stored).isNotEqualTo("steve").startsWith("steve_").matches("[A-Za-z0-9_]{2,12}");
        verify(writer).insert(DEVICE, "steve", stored);
    }

    @Test
    void retriesInAFreshTransactionWhenTheNameIsTakenAtTheSameMoment() {
        when(repository.findByStoredNickname(anyString())).thenReturn(Optional.empty());
        when(repository.findByDeviceHashAndDisplayNickname(anyString(), anyString())).thenReturn(Optional.empty());
        when(repository.existsByDisplayNickname("steve")).thenReturn(false);
        doThrow(new DataIntegrityViolationException("duplicate"))
                .doNothing().when(writer).insert(anyString(), anyString(), anyString());

        String stored = nicknames.storedFor(DEVICE, "steve");

        assertThat(stored).startsWith("steve_");
        verify(writer).insert(DEVICE, "steve", "steve");
        verify(writer).insert(DEVICE, "steve", stored);
    }

    @Test
    void reusesANameThisDeviceAlreadyHolds() {
        DeviceNickname mine = new DeviceNickname(DEVICE, "steve", "steve_a1b2");
        when(repository.findByStoredNickname("steve_a1b2")).thenReturn(Optional.of(mine));
        when(repository.findByDeviceHashAndDisplayNickname(DEVICE, "steve_a1b2")).thenReturn(Optional.empty());

        assertThat(nicknames.storedFor(DEVICE, "steve_a1b2")).isEqualTo("steve_a1b2");
        verify(writer, never()).insert(anyString(), anyString(), any());
    }

    @Test
    void answersFromMemoryAfterTheFirstLookup() {
        when(repository.findByDeviceHashAndDisplayNickname(DEVICE, "steve"))
                .thenReturn(Optional.of(new DeviceNickname(DEVICE, "steve", "steve")));
        doNothing().when(writer).insert(anyString(), anyString(), anyString());

        assertThat(nicknames.storedFor(DEVICE, "steve")).isEqualTo("steve");
        assertThat(nicknames.storedFor(DEVICE, "steve")).isEqualTo("steve");
        verify(repository).findByDeviceHashAndDisplayNickname(DEVICE, "steve");
    }
}
