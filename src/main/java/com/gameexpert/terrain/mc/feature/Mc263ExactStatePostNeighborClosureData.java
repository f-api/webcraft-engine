package com.gameexpert.terrain.mc.feature;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.zip.GZIPInputStream;

/** Generated official POST neighbor rows intersecting the exact production closure. */
final class Mc263ExactStatePostNeighborClosureData {
    static final int AUTHENTICATED_ROW_COUNT = 93;
    static final String SOURCE_SHA256 = "70d3e811c086491e7ea1f5d09bc82e650eb56286bfd8e1c03c79add8249e2e9a";
    private static final String GZIP_BASE64 = "H4sIAAAAAAAC/81ZTW/bOBA9u79FBeJuDkEBXRZYbPeyLXYL9CAUwkgaSawpkiCpOM6v3yElO1FsxzZprw0hjuRAb958zzDfvv77"
            + "Pf/7j7/+/PL7139mP2YdE1hqqO1nKKFkkNcoSswbsJjV9IVoUiG1bRMm8iVwntbADSZSoRhvlVyixmp4+jm789dv4zX3Tx/d54dv"
            + "h0Qrjcb0GnPFnfidwC/X/Bhga6Fssco75FLkxmI30Wob9e4UVNV3asHOhlswXba54VBkdqUwLaS1skuWZAzNZdNMbTFfw9beFHOH"
            + "/+mAAA7lwrsxL0AI1AdIH2PigveYL6Xkg99rT6geSdHjR/cxfw9Ay6WIQihBP754Y0un+YD5Yqq7IyBbZpATaIWojAvHYHKyKCZA"
            + "l3TwDmEWmDbTVG6B12vZpgUiYqwG1rR2L5V7+rina6RyPxuQBpDZp4cTWbkQzBCMJUYCEw823BrZr297lVrd4zanZInjm69Dth4D"
            + "1jvlkJk6BRqsnCZA0skK0+GPO+taiEc0pdxE90KzcmGCw2kLzzKO4XAriMu9CvQil7DIK/nWmj7MuLNi0tK3mHKs7YHO8eB/HkYz"
            + "P5D8+3fNuxF/jba1EW5AcRKZUbKRmndh1f8F7XL1YSOD8l3t8dgodJ+pzslmHcMZPDGTrn4GB+E0uS5qwbeibqC+vuV05ep6rlI3"
            + "LXH/j1MHSTflU0/pZlwa1W0qBp0UVV5wWS7CQaRm158hao62dH0tt0DTTag2DQcTbM9Gg2A3MuStuVzZLURjdeYNp9FMVMYSJweG"
            + "qUNP4rc9JmrSlUa5YTz2AkID4RdrDCwzSgwUFiyTYmCW9yq4qf7qRUOVp+jJ+2KP5smBHf0YO4xyaOcX4b1iBLlgjxgl3MTgxEEx"
            + "E1dFOTxCxvEReei8yl0tyf36Twu4QhusjQe6QOJy1sVzIwiq0c+Up+Q9Wr7IybCDW4gLHHbM7tXRtkHpHqliJ41ZvS5C1y7hbj/x"
            + "gWBYMz3N2ZdCpwaGpF7VxNlePrGKPfvqrRTqvJEcO9eIbY/Z5Dsljeu9ICrSIzlNneP2UMXEIjIIlOTMuCPNAgxwG7uQbeCij8+2"
            + "kS5Z5HdJu4HZagetK+eoIt7Eh0iwvgsL2hHh2R34uy5iY2DGo/PhHCYKyPUzSWCsioGpKN2R0xwWAyKfcIUExcwqBkZRNYoCMEr3"
            + "JZ7DusvWrQi250yFwfRa8dierp2bsZqmtCzLXrFNfijQNq2ltGGzkRMR013o/aEVR814GxQOnco4s68rUgye787WZcnUiE6EKzih"
            + "RqOViEDP0TaoCPK8ZhqDmPi3aR+hqimylmYFp+K7Q/ypgewlGCoSwQoOOXmNY+9R9Pn/WzsCX3b2WwuRsoqdcqjp96V1JniUW+3i"
            + "uEizoBu0g/HWi1gAE7eVKjeMttTXp2bzg5Jhbnk9V/B6mLjlcWgEJSUAp3HZ/zLpPNnUqHNRXSHnchnZMEaQwwX9P3Vu77tdIQAA";

    private Mc263ExactStatePostNeighborClosureData() {}

    static List<String> rows() {
        try (GZIPInputStream input = new GZIPInputStream(new ByteArrayInputStream(
                Base64.getDecoder().decode(GZIP_BASE64)))) {
            List<String> rows = new String(input.readAllBytes(), StandardCharsets.US_ASCII)
                    .lines().toList();
            if (rows.size() != AUTHENTICATED_ROW_COUNT) {
                throw new ExceptionInInitializerError("authenticated POST row count drift");
            }
            return rows;
        } catch (IOException failure) {
            throw new ExceptionInInitializerError(failure);
        }
    }
}
