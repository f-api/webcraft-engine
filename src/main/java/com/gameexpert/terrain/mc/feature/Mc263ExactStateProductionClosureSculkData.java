package com.gameexpert.terrain.mc.feature;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.zip.GZIPInputStream;

/**
 * Generated Ancient City sculk-patch configured-feature rows of
 * {@code exact-state-production-closure-v1.json}.
 *
 * <p>Derivation: 26.3 places Ancient City sculk through the structure's post-placement
 * {@code minecraft:sculk_patch_ancient_city} configured feature, whose {@code minecraft:sculk_patch}
 * leaf is transcribed by {@link Mc263SculkPatchFeature}. The appended tranche is exactly that
 * leaf's enumerated world-write surface as {@link Mc263SculkPatchFeature#preflight} spells it:
 * {@code minecraft:sculk}, {@code minecraft:sculk_catalyst[bloom=false]}, every
 * {@code minecraft:sculk_vein} multiface combination with at least one face (faces 1..63) in both
 * waterlogged spellings, and the growth-inhibitor
 * {@code minecraft:sculk_sensor[power=0,sculk_sensor_phase=inactive,waterlogged=*]} /
 * {@code minecraft:sculk_shrieker[can_summon=true,shrieking=false,waterlogged=*]} states. The two
 * sensor spellings were already carried by the Ancient City template palette, so this tranche
 * appends the remaining {@value #AUTHENTICATED_STATE_COUNT} rows append-only after the
 * connection-shaped POST tranche.</p>
 */
final class Mc263ExactStateProductionClosureSculkData {
    static final int AUTHENTICATED_STATE_COUNT = 130;
    static final int RECEIPT_BASE_STATE_COUNT = 3_203;
    static final int RECEIPT_POST_STATE_COUNT = 1_812;
    static final String RECEIPT_SHA256 = "8da716044ae5b5321119b834ad7cb36fc1414f62cb31886a3d27cde34cb4cfe9";
    private static final String GZIP_BASE64 = "H4sIAAAAAAAC/7WZUW7CMBBE/3sWTlCJk6AKucHQiMSuYqeot2+boFYVoNjZeX9BxHkMwzBabd8G3wzumJ9TM3bnp/7/633jsus+U969djH226Prkn+5"
            + "uSu9Da0/+2HXuLBPY9/HsM3D6DfzG204zSc3F5f90MXTyR+kz/q54fZRH74Nu0O8hOsJ71K+XoY45LfrdYrj7/X4/uiTbi7++zQLmnTKOfNT7+t5YIIe"
            + "hAr6+95YPVJ/poct/d4UeopAqCCtPyUcoRw6PiUcUg5jjj47cwlU/FevlVMNQgXZ7anlCOUQ2anlkHIYc9DsoLVTyyHlSL0BO+cOBY4N2DglFMQYTWam"
            + "Q2tngxo1JhAqaJ07Fo5Qjio4Fg4phzEHzY68bywcUo7UG3HfLFDg2Ij7ppaCGCPPDDreWDikHLM14GyzQAEyA042tRTEGDIzZM2AU00tRekL1zHgSLOe"
            + "wkkhTDFkZbpTsOtYFKMCoYIqzBFxhHJMqRFxSDmMOWh2NEUj4pBypN4ouqacAsdG0TYGCmKMPDP6eUbEIeWYrVHPM+UUIDPqecZAQYwhM0PWjHqeMVCU"
            + "vnAdo55nJBROCmGKJCvsekbEIeWscobczZRTRIEhNzMGCmIMmRl1v5BbGQNF6Yu2X8iVjITCSSFMUWeFHF/IbYyBYrWFm13IVYyEwkkhTAGzAtYKN7WA"
            + "SxgFRCUEjQnWKNjAYty/fAGmVbZfOjMAAA==";

    private Mc263ExactStateProductionClosureSculkData() {}

    static List<String> states() {
        try (GZIPInputStream input = new GZIPInputStream(new ByteArrayInputStream(
                Base64.getDecoder().decode(GZIP_BASE64)))) {
            List<String> states = new String(input.readAllBytes(), StandardCharsets.US_ASCII)
                    .lines().toList();
            if (states.size() != AUTHENTICATED_STATE_COUNT) {
                throw new ExceptionInInitializerError(
                        "authenticated sculk-patch state row count drift");
            }
            return states;
        } catch (IOException failure) {
            throw new ExceptionInInitializerError(failure);
        }
    }
}
