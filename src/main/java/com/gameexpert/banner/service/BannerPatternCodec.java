package com.gameexpert.banner.service;

import com.gameexpert.engine.inventory.ItemComponentData;
import com.gameexpert.engine.inventory.LoomRules;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class BannerPatternCodec {
    private BannerPatternCodec() {}

    static String encode(List<ItemComponentData.BannerLayer> patterns) {
        // Revalidate through the component value object so persistence cannot admit an invalid payload.
        List<ItemComponentData.BannerLayer> validated = new BannerBlockData(0, 0, 0, patterns).patterns();
        StringBuilder encoded = new StringBuilder();
        for (ItemComponentData.BannerLayer layer : validated) {
            if (!encoded.isEmpty()) encoded.append(',');
            encoded.append(layer.pattern().wireName()).append(':').append(layer.color());
        }
        return encoded.toString();
    }

    static List<ItemComponentData.BannerLayer> decode(String encoded) {
        if (encoded == null) throw new IllegalArgumentException("banner pattern data required");
        if (encoded.isEmpty()) return List.of();
        ArrayList<ItemComponentData.BannerLayer> result = new ArrayList<>();
        for (String value : encoded.split(",", -1)) {
            int colon = value.lastIndexOf(':');
            if (colon <= 0 || colon == value.length() - 1) {
                throw new IllegalArgumentException("invalid persisted banner layer");
            }
            try {
                result.add(new ItemComponentData.BannerLayer(
                        LoomRules.Pattern.valueOf(value.substring(0, colon).toUpperCase(Locale.ROOT)),
                        Integer.parseInt(value.substring(colon + 1))));
            } catch (IllegalArgumentException invalid) {
                throw new IllegalArgumentException("invalid persisted banner layer", invalid);
            }
        }
        return new BannerBlockData(0, 0, 0, result).patterns();
    }
}
