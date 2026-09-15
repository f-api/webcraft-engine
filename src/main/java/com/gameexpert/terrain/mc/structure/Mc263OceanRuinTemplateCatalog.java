package com.gameexpert.terrain.mc.structure;

import com.gameexpert.terrain.mc.structure.Mc263OceanRuinProgram.BlockPos;
import com.gameexpert.terrain.mc.structure.Mc263OceanRuinProgram.Marker;
import com.gameexpert.terrain.mc.structure.Mc263OceanRuinProgram.MarkerKind;
import com.gameexpert.terrain.mc.structure.Mc263OceanRuinProgram.TemplateCatalog;
import com.gameexpert.terrain.mc.structure.Mc263OceanRuinProgram.TemplateDescriptor;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Official hash-only descriptor catalog; deliberately contains no template block payload. */
public final class Mc263OceanRuinTemplateCatalog {
    public static final String CATALOG_SHA256 =
            "2190605b78f28ff770f1ddef47b354464a82a8924f29c5fd8f37cbb6c0f18f88";

    private static final String CATALOG = """
minecraft:underwater_ruin/big_brick_1|16,16,16|148674|68bf1e2029193b0814e8e118e89ce78737647da9396885f109c01f5ee3d4c141|chest@5,0,4;drowned@6,1,4;drowned@8,1,11
minecraft:underwater_ruin/big_brick_2|16,16,16|148709|8c83d49665fd9ed0f7415dd0055727616016f67fb3ea037a0e5d0b777e4b9d12|chest@9,1,10;drowned@10,1,8
minecraft:underwater_ruin/big_brick_3|16,16,16|148733|9b1030216ffde13de23e8c883e94837da4d1a05ca7a2e32c0a1c7672597f71fe|drowned@2,1,7;drowned@6,1,9;chest@12,2,2
minecraft:underwater_ruin/big_brick_8|16,16,16|148671|89e034f674998e091741e5b54d8f47fddb0a9493f4708bc3c779b1079d1039fa|chest@5,0,4;drowned@3,1,8;drowned@10,1,9
minecraft:underwater_ruin/big_cracked_1|16,16,16|148419|268aaf36815861f51b199772e5b9e7f679bc47b79b7642eaa72e5ccdf48f90a3|chest@5,0,4;drowned@12,1,11
minecraft:underwater_ruin/big_cracked_2|16,16,16|149009|849563efb8581ef992c662176b7a4fa445ccf4e06d82e8da3a8b02ba7001ea63|drowned@9,1,7;chest@9,1,10;drowned@12,1,9
minecraft:underwater_ruin/big_cracked_3|16,16,16|149033|1880474852d8077b41ca1879ffff2313496d6e762a76fea153a8175f1ec23474|drowned@9,2,2;drowned@9,2,7;chest@12,2,2;drowned@13,2,6
minecraft:underwater_ruin/big_cracked_8|16,16,16|148387|4ef59c12c5b8536e1ddff4039166e4fbd899a6d91f1190b661d2a386099dccda|chest@5,0,4;drowned@2,1,4
minecraft:underwater_ruin/big_mossy_1|16,16,16|148417|ac244c76252311ffedcb0bf0976af4b5548805bd38fab974954d7356d2bd76fc|chest@5,0,4;drowned@6,1,8
minecraft:underwater_ruin/big_mossy_2|16,16,16|149007|cc7748ec729e981536cf62a8999a1b3f6c6527ece1ef42ad72961328d126c2c6|drowned@4,1,5;drowned@9,1,6;chest@9,1,10
minecraft:underwater_ruin/big_mossy_3|16,16,16|148447|57bbaea2b59f4de67126663617cf39aa5a97bb6b9be6aab95b3f927ae4d2cbb7|drowned@5,1,9;chest@12,2,2
minecraft:underwater_ruin/big_mossy_8|16,16,16|148677|c2bfa52b924cc338269706021b778cbcc5d606598f831e9816bfa86913859625|chest@5,0,4;drowned@7,1,8;drowned@11,1,10
minecraft:underwater_ruin/big_warm_4|16,16,16|148683|d81ad9552371cf7144f192cc62319c576e7847c4d1e6ccf1f488ccedbde3bb80|chest@11,0,8;drowned@3,1,5;drowned@4,2,6
minecraft:underwater_ruin/big_warm_5|16,16,16|149292|79fd1829ef40545c62231ab7b87318ba1d3c6b42608a83eef1a5c7fd1a05a238|chest@7,0,7;drowned@5,1,5;drowned@7,1,9;drowned@10,1,12;drowned@11,1,4
minecraft:underwater_ruin/big_warm_6|16,16,16|148676|0333f053e008eaee6cce1e2ac58871bd44a6bba02597b873588b66e83dc00ce3|chest@10,0,9;drowned@5,1,4;drowned@9,1,9
minecraft:underwater_ruin/big_warm_7|16,16,16|148734|22b075e17edd56ed1083c41b813060ee6d4c5f19b90ba2f934e1c458b99791b9|chest@11,0,7;drowned@4,1,5;drowned@10,1,8
minecraft:underwater_ruin/brick_1|6,7,7|11164|6658785f2599ae07847eb11fa4fb9f2096b8ce5223674ed34e35742b31fd095a|chest@3,1,5
minecraft:underwater_ruin/brick_2|6,7,7|11748|4d33ef9bab86b8c28586172d836673617e07eb78ace7c8e7cb6a60fc8bd72bc4|chest@2,0,1;drowned@2,1,5;drowned@3,1,2
minecraft:underwater_ruin/brick_3|6,7,7|11457|32a69b08ca34720659858c8c04db389f9690143ef3deb40b530bab194f571371|chest@1,0,5
minecraft:underwater_ruin/brick_4|6,7,7|11799|d892b8e0b1c9e9e3044425f2f485e59bd2aa243507f8dc911c20906afeda9124|chest@1,1,4;drowned@2,1,5;drowned@4,1,5
minecraft:underwater_ruin/brick_5|6,7,7|11173|a84ae0ef923de1fbbb4e81f254b4742e7029cf3df50a27ba99ccaaf44bf6c743|chest@4,0,4
minecraft:underwater_ruin/brick_6|6,7,7|13349|1d9b8f7e53a068ffbec5c643400a765f22a300655e463fce1cf8a4151b599910|chest@2,0,2;drowned@2,1,3;drowned@1,6,4;drowned@3,6,1;drowned@3,6,4
minecraft:underwater_ruin/brick_7|6,7,7|11237|f4442f30fb7c30b6035a8234729b8b0a62aa3ef600d709ebddd3c81749ff6d32|chest@1,0,3
minecraft:underwater_ruin/brick_8|6,7,7|11493|9c14d11f637e84df818c111db2f15afa66e687d5fbc6eb56c5337f1022c7b894|drowned@2,1,4;chest@3,1,4
minecraft:underwater_ruin/cracked_1|6,7,7|11172|cc958de9f428a1a20e839914be369f96207f9148a7068b9685b87043089e8df4|chest@3,1,5
minecraft:underwater_ruin/cracked_2|6,7,7|11172|ddf4e210cde1ceedae9cab8b600a66cf76456296eb295193bb0de4c39c49e2de|chest@2,0,1
minecraft:underwater_ruin/cracked_3|6,7,7|11491|6fc5d4af7d28369b847dcdeb45a9a58882a9abadf4089ec85964183e63001f30|chest@1,0,5
minecraft:underwater_ruin/cracked_4|6,7,7|11193|1c9a173d306fec57f47dd2283d6be35d22e9c796e5d368e21cb793dd2b8ea118|chest@1,1,4
minecraft:underwater_ruin/cracked_5|6,7,7|11181|fddbfb03ce6aab5f0984b8b727c9ab2dea89c11f889b0e4178bf1da0b4faf2b9|chest@4,0,4
minecraft:underwater_ruin/cracked_6|6,7,7|12125|18366b35cd83c383cca68eabf5ef80d6c35a9431aa2a8d9295465af39b063b43|chest@2,0,2
minecraft:underwater_ruin/cracked_7|6,7,7|11537|61c86554e527375e554ad770db42da34c254385a869f0d4e36c0387c71188054|chest@1,0,3;drowned@2,1,3
minecraft:underwater_ruin/cracked_8|6,7,7|11209|f83bc4fff3b1369509c5c380ba755faa0e3858337a8951147df2c800efe396cb|chest@3,1,4
minecraft:underwater_ruin/mossy_1|6,7,7|11201|e2b2f4baca474620a5cab093af2b6dd456a8cfc09bf088403a7bf17a1ca83f75|drowned@1,1,2
minecraft:underwater_ruin/mossy_2|6,7,7|11170|a848b0b603769ba01e0c2144e20562bc849c3e2e9c203675d11f47ed57a1b2dc|chest@2,0,1
minecraft:underwater_ruin/mossy_3|6,7,7|11463|5a4956678521dc93f53a3cf691402882143155b2c00f7b0c5395fa0140db4631|chest@1,0,5
minecraft:underwater_ruin/mossy_4|6,7,7|11221|5994c620c98f3d5bf1711ef81235eca56fa10908abbefda0962cb289d5065f52|chest@1,1,4
minecraft:underwater_ruin/mossy_5|6,7,7|11471|d54ba4e503b8b78ef0e56004d8c4f51ad5fb94d74a5a1c36b9c18c5611b9412b|chest@4,0,4;drowned@3,1,3
minecraft:underwater_ruin/mossy_6|6,7,7|12479|4cff9a56c75709db4cb882a2e18afbf1604a9bede04df712f9cdd82b65e26719|chest@2,0,2;drowned@2,6,2
minecraft:underwater_ruin/mossy_7|6,7,7|11535|0f47bdab2deea80d2ff6ee7f6027dbf4d7aaf80deaab5f7e9cd372186173cda7|chest@1,0,3;drowned@2,1,2
minecraft:underwater_ruin/mossy_8|6,7,7|11525|f5747dd0b783398af67c7196920b0606527fc90d8397f552e421eb44e7108d20|chest@3,1,4;drowned@4,1,4
minecraft:underwater_ruin/warm_1|6,7,7|11133|30351c48bb87a6e07bcbafa60c42ad31ed5859714c776725d830d277bb8e7cca|chest@3,1,1
minecraft:underwater_ruin/warm_2|6,7,7|11756|6377d412b865e6ebff96517b8e01b523691b10b330adf3c6487011b8046a0fab|chest@3,1,4;drowned@2,2,2;drowned@3,2,2
minecraft:underwater_ruin/warm_3|6,7,7|11454|98c47fa8e07563e8d087fc88a56d96560d4b6e521da4c9f92d24bbaa1ae012c9|chest@3,0,4;drowned@3,1,2
minecraft:underwater_ruin/warm_4|6,7,7|11690|b72db514cb28082720ecdc94bbdd501aaa3d7427d79df83cf4ebc333f24c3154|chest@1,0,2;drowned@2,1,2
minecraft:underwater_ruin/warm_5|6,7,7|11391|297d06a9b6df899f74e6ddcdcb86740a5e294527a9e8c46ee2f75ae90934c754|chest@3,0,4;drowned@2,1,2
minecraft:underwater_ruin/warm_6|6,7,7|11167|77df2e7105f095afb0bd21b46096181e61593da91108eb58397048c1cd242925|chest@4,1,4
minecraft:underwater_ruin/warm_7|6,7,7|11133|6e21542e408af4e9e33c460c032f849b7c2bb5a748bc9a7e6ccafc1d89cb8e3e|chest@3,0,3
minecraft:underwater_ruin/warm_8|6,7,7|11656|47c2a362de9d09d86f0b28d06b3a6a63f632d4cdfbfece71c8d0154fe9c54432|chest@3,0,3;drowned@1,2,3
""";

    private static final Map<String, TemplateDescriptor> DESCRIPTORS = parse();

    private Mc263OceanRuinTemplateCatalog() { }

    public static TemplateCatalog official() {
        return key -> {
            TemplateDescriptor value = DESCRIPTORS.get(key);
            if (value == null) throw new IllegalArgumentException(
                    "unknown official ocean-ruin template: " + key);
            return value;
        };
    }

    public static Map<String, TemplateDescriptor> descriptors() { return DESCRIPTORS; }

    private static Map<String, TemplateDescriptor> parse() {
        if (!Mc263OceanRuinProgram.sha256(CATALOG.getBytes(StandardCharsets.UTF_8))
                .equals(CATALOG_SHA256)) {
            throw new ExceptionInInitializerError("ocean-ruin catalog identity drift");
        }
        LinkedHashMap<String, TemplateDescriptor> result = new LinkedHashMap<>();
        for (String line : CATALOG.strip().split("\\n")) {
            String[] field = line.split("\\|", -1);
            if (field.length != 5) throw new ExceptionInInitializerError("catalog schema");
            String[] size = field[1].split(",", -1);
            if (size.length != 3) throw new ExceptionInInitializerError("catalog size");
            ArrayList<Marker> markers = new ArrayList<>();
            if (!field[4].isEmpty()) for (String encoded : field[4].split(";")) {
                String[] marker = encoded.split("@", -1);
                String[] position = marker.length == 2 ? marker[1].split(",", -1)
                        : new String[0];
                if (position.length != 3) throw new ExceptionInInitializerError("catalog marker");
                MarkerKind kind = switch (marker[0]) {
                    case "chest" -> MarkerKind.CHEST;
                    case "drowned" -> MarkerKind.DROWNED;
                    default -> throw new ExceptionInInitializerError("unknown marker");
                };
                markers.add(new Marker(new BlockPos(Integer.parseInt(position[0]),
                        Integer.parseInt(position[1]), Integer.parseInt(position[2])), kind));
            }
            TemplateDescriptor descriptor = new TemplateDescriptor(field[0],
                    Integer.parseInt(size[0]), Integer.parseInt(size[1]),
                    Integer.parseInt(size[2]), Integer.parseInt(field[2]), field[3], markers);
            if (result.put(descriptor.key(), descriptor) != null) {
                throw new ExceptionInInitializerError("duplicate catalog template");
            }
        }
        if (result.size() != 48) throw new ExceptionInInitializerError("catalog closure");
        return Collections.unmodifiableMap(result);
    }
}
