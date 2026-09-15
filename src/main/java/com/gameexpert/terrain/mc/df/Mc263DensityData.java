package com.gameexpert.terrain.mc.df;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;
import java.util.zip.GZIPInputStream;

/** 26.3-snapshot-7 Overworld density 폐쇄 집합에서 컴파일한 typed AST payload다. */
final class Mc263DensityData {
    static final int MAGIC = 0x44323633;
    static final int SCHEMA_VERSION = 1;
    static final int DEFINITION_COUNT = 23;
    static final String RAW_SHA256 =
            "0539bbe0b996aa8960b70dcbf78a280d518351eeafdb4eeb1920f22a11c19893";
    private static final String GZIP_BASE64 =
            "H4sIAAAAAAAC/+1bXYwTVRS+7bJD7VbBbRdYVNgNBgSjwC5Rwe1MEQw+bGI0GjUhqUN7u1ssbZ2ZXVz8yfrzYIwG44MGlQSFmKhkMf4kPNDOukqApCQkRsEHDCSiJmqCD/jiA557585P25l2ZtvdbZRJdqbn/pxz7rnfOfd3t/bd1Y98aDHq2JXO4oQkppRNY/OQbwxdn8iIu/JxJRfHySF89erlHoQW/HT8BYTuf/4CQjea5eXhdEqJP9MV6UELzdRcKiVjpbrcnq5Ir025XkvKKJZ256RMcu0OUcbx/mQ8m0vL+CbhDKKPMKl9Y4+wb5J9e7QvWmHHKyGOYnktziqSmE1gORzkOD9abJYk+XE9W/iNibrAWPKF/CFbs1z9G3KX8fv2vUuKcT50l7NwOS8ODWNFScel3MjQcBbLcjw1kk0o6Vy2k+sIdYf96BaLvYziYAJJlNLKWIypI1xG+tOm6mnaZ17Aj5Y4MFkvXHmUPvoXCcCxZoXLFQKF8ToVLjEJl3QJk3UqXKgwNWnjzJuiz6sp+ryaos+rKfpsTcFBjR6HGspwOvEUgVGVbscGdo6jwvuHA+q4pqiVRyUus7lcMoPbF7X7ysLAVB6hLQfGkd/qrlrZKnlEzCnlVo24DwjOiRtpz9JKfrVaUiqdRgXmXyjQEXJWc0kVWykNThoXY+1aD+hfymoajHbYMtICUq+zffPpTEaU5HCAtN1iSy09tpFx+a6fPkavd1eWJMjHtkYi5qevGyrMyyo6m1cIgXnpa6WbwNWX7OzgQt0OkOxLxnflkiMZUclJNv45T4VElfmnoBVoB30X2fOKWbzAcGvn4k4O7VTcwZ2ditv5Zo3i3zNl2BfFiDI+tMmdkc0OMw2Koh//8+sNXIjrCjibH2fwqEjGE4vZtWcz6QDbEQzQ64uR7CkYQBtS0Qg2DfAIczWaZ5SvQlfxi/4+pMrgPtMcgDXvXGUn2CxsaFmFnsIgCVPw4kIOI5fBpapukYS4ZbZq57IKJGcVuavNZzPnotVtMvZYg4fBRMxQ8WcqkNFtJzmJ88owVwMwJOip5Jc13Jj12bTuJrs8LOVksLjXJlmSGIeqptiqkhIT0GNdkaU3o82A8CW+erYmYal4397tzNGfK50udmvjj89Xs0VQIqiaRW21oSMJEeEvEshoIk7te5c3qZNalHTHpVDGJWpSay4+AE7R37guxXG3utTiUihplKcWQRuiZTwHrkztbVwXntmFUb+kUoxnrzPPeCqXSWKy1PCrKa0C66p1B8+6U8nEKakXLekqCT1sHJ5zJZYdfdi2x2YB9EeC0ZYBPejSBNADl5YBPehiAT2hGgA96arp4A3qNQ/0zVPCBD3kWWw9C6D/cO/2lgE96NIE0AOXlgE96GIBPaEaAD3pqungDeo1D/TNU8IEPckbcQv6jiaA/u3Isy0DetClCaAHLi0DetBF5+kaYRa/aECs0K/7mnBiIsKXWkEJzw7f3xRIMScBJY5sX+3d4WdCiciei2aevSKpdFbMxJM4K6eVMS5446LA4qUOK0D4O6dvmqAoLGTtCkLWZbKWE8b1RfIBWFk5WEHO5PI4GU8MYyxjspUowBQ06KZwwOdqxx/FQHpHMMgFrvejSMXGf0Ycw8ai3jhIiJGNzE7Oj7oqimuCjYW8vsHLvxZ9Wt8D6eQCbpRH6oqzQeNIQ6BrXzet4XxuN+ymfSTh1FNlm5qkp2jUIj/cVEACNNhXfzt6mf0qfac4NISTRE+6oCePqwV9W+HitglzRHQ/zZunmuODK/dtAzcsmZIgBGGTElwPNscILrQ6vdsmBgyKHDWp2yYic6oS/0GZShBp1LBGtZBKxUnr1pCw0hgKZqnHG+vj/whSbFSaa6RUq1SOlFobmiTo3MIhFV35xlXYCaqv62NydMPGx9WX9F3FYuLVlOq3UkUjQBUgOBXveegPC7XCsFkd8M5XnzAm6C5nG2BC/kT8TZD01flJwaCEdQfPWWYiHph9PfYKMPsz/K1gUBD0Q0WPM0JO90NmrVgZtVJfFNEuXZI7qv0i6gpLr0b4F49zjCJKUAqWQGcsawZXSrTT8VwTS5BirKLMibtO8WV5ROy9l57cCtTI1JsFj223iu22iq1+iFhtcVctlh+cUbHVfsio/mkYmR33HjwbNMUeY8O0SdHWjuip5WLd79Jf85JrXvJ/9RL32/rXvGTmvGScHfvOrpeQqkSsNv3Z9HJnji6GKHXv1vc+oriZfDncZC/RxbK1lzH30ikqlqaSdRkRG33gy43a6dRMewlNrRbLmxPLuluiHr2kzdSQf3DDJYq5ttfmw/vux2JEC0btzx/y6jNW1qy7+POnjsH75+MHKHgpJYjbJrx6kB3rk2+8Be/9536grCkF3bWhaX5h7t7rFF+Wx7osChSAqAl+Ed27fTXF/GbqepQaOBxYT8QKt0mf2YmdI7/gS3Mj1uOOqgU2VrGzrLUFRHdGnvW6IduSbWD7uf+PaCzMTjT+fAW9Si6Ed6+Cd+bVH2k01ihy3akJ0fj3v84Bs+D8CA2ZGmWJeTPBuoFoPGhFilA2sxXKZrcCmxxByLwdfsGsuJFozMRCz5dMgDKKIUXIk2PQarGNROPBuYnGg3MTjQcbi2SDrRCNW6YNDUTjwVmOxgN0XVgejT22/Q47YXkJZ9KQIUpjcXlESokJHM/gUZxZzjX/FLGTCywMuLmeGql5P9UpV7sySs//kLoBoalx7b8aUPEklT3/OsS/7sxcq15bNPqGtGULu3a9BdqkqY4CtXayvd6ZXWAmUQZVN2br4ybAhbhQ7e11dc0nn6LiJ/CajDnxLDv1DNPu89W4etwV8NU7AFxQ9s8UWnrsnXxZ8+r3r8/Fv56h5XZFFLwrjyVRGZGwx16xHCdbmFT1jW37R/EQVugVf49CLUfeJo9Kmf8Czb/JehA4AAA=";

    private Mc263DensityData() { }

    static DataInputStream openVerified() throws IOException {
        byte[] compressed = Base64.getDecoder().decode(GZIP_BASE64);
        byte[] raw;
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(compressed))) {
            raw = gzip.readAllBytes();
        }
        try {
            String actual = HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(raw));
            if (!RAW_SHA256.equals(actual)) {
                throw new IOException("26.3 density payload SHA-256 mismatch: " + actual);
            }
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
        return new DataInputStream(new ByteArrayInputStream(raw));
    }
}
