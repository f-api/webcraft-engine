package com.gameexpert.terrain.mc.feature;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.zip.GZIPInputStream;

/** Generated authenticated exact-state tranche. */
final class Mc263ExactStateProductionClosureJ4Data {
    static final int AUTHENTICATED_STATE_COUNT = 1605;
    static final int AUTHENTICATED_BLOCK_KEY_COUNT = 343;
    static final String RECEIPT_SHA256 = "8da716044ae5b5321119b834ad7cb36fc1414f62cb31886a3d27cde34cb4cfe9";
    private static final String GZIP_BASE64 = "H4sIAAAAAAAC/71d2XLjuJJ9n2/xQ5Xs7jsxEf4SRYcCliCLY4pkcCm3/PUDkrJFYs1EHs5LlQUgTx4kE/t2LSp9bNW5/x91VMdC"
            + "HU513e7P5s/q/VWrrn+6qPL8Wtafun26mED9Wupz/1Q3uno9q7LTT80Yp0/zr3/+6ypB7NsBAdgW7xcwxwWkgOTQNGA7+hFBFEF2"
            + "DECySVZ121+wDpmERJEUmTKNKaGJcsokJIokypYov+zqAe2XSUgUSZEt05gSmii/TEKiSKJsifLLT41uv1OIIIoiQyYhBSRRLplC"
            + "BFEE2VHij2ddHfXhXfV61S8oqsOnKss7LQZDF2+u0IGAc0kEAk6WzMYr6/e9+rfoXv+NRd5ikV+eyKZU1UfnCW911w2tHhOYLKTY"
            + "daopTR73Xa+Md/z2pPis61M4A4/YWzR2nYWiXf4qy2K4LgP6Xh0v+nS46rKuDl2vr0vn+4eWdPIrYtrJZYhpR2/wJm2Ga/NREPn6"
            + "EocZ+1KHOftSO6y/Rh95KwfdL0LfdFnuZ5yrrvrXoy5G73haFv2gRyWE53KeKz0X6lzpqQSThc+lqUbz8rwSZeZ4JcvM70o2kdui"
            + "PV4OpVZ/dLc/Fabgm/ru9feTaSM682tCmyq2T1N/tKYOeoehjC1OGmQHobJDUHmGUHlGUHmBUHnJouJtway4WyTuy41zmq85uCvV"
            + "276/Nfr1re77+krJY1TGn6OHSF83PB22gKOgVMePw5s+rfpL9fE4NMU3/lOjWmP+uu7zJC9anaKSc82TpTQsmtQ611lZWsOiSa1T"
            + "bZelNCjp13lUbbNuLafg91J9mUbXeESrjsYBVajtTyR3Wv9Eeqf9T6S3ewBzclMvmJDTKNZ1TuzY4z28qarSbTxXnoSB/HhSBnLi"
            + "SenPg+llluvArj+ch7ZSx/WopSx6TwH3JZ9LAT397L/09JPrBZIPOrP6IAq63r0W5FQeVMmUTk7VQZVM6WRUHERBr0ZPtWFCi6O2"
            + "g2rT0BQnO/RRkO0Y2+3rauzQ18cPX4Nc1x/dRZfnZVirP012xiqgOu0vqjuM7WepD7/ueVoE/XaDdh7fZSFO8w8pwGKso3idgrSM"
            + "02ZbIulOQUIgoMDUtW3nrq/cGXYXZaC6vlXTHA2FQDYgj+CYPRy7BBqF2mIRAGQ8FiKTIsJ8dDgKucVkNch+LEQmRYT96HAUco+5"
            + "VZD5OIA8ggjjkdG81Fbdyrb+rPK6N2RJpxW2JRkdHLpoUiuji0MXTWqld3LIkn6dbjdnCr4O3aWt6+u9Z3IyYXPDP36+O+pk1Pvf"
            + "U1bvfw/NnHRk4nNUKL7ruvnwU4rt2LvwUvJT6Hamj8PnkJ/+2s5vAvCg77pAzzJ8cMzuxh4aVel97IPM2uwWga4/QwMvgwsF0a8e"
            + "yIPfu8QaUu7rH7ZNUZRJlVBCd1IllNKdVAmldCZV5oTW6NJ49OLnUR37oduPa4a//nHCD+dp6X4V3vb1e6uay+3Qq7dSr+P+PFat"
            + "Qibxp3Ls4U/mGMOfzLbEUQ3lqa2rZdDFfNhbYErcjrzFIr88kZHV2OOl6HRpOJ+0brpxbdcX2ZmBd9fXlT+yn6YI7D7ZT3w/nM+h"
            + "cI9YqW6rn3Vn0uqbNnVe19XLBd1jrcq5KrQD61avg9pOH05F269C396CGbfjmJMFTHm7rHvEKUNy4426PUzbRbjsBPBQ8m16JAHG"
            + "B9GnjYKQ4PnEuWM3GDKXcmj2BOXoTHwsfYCrcxWgMoBxG9HcGAMc7e75c2Yp5NBsF8rfmfhY+gB/5ypAZQDjN6K5TAY42t/z5zhT"
            + "yIHpSZS78+Ch5AHOzsQH0ce4jGTemY6NdvTs+WgXeBwPz0P/ygyO7kP/6c955D/9+TMp459iGNNspOUxzeBXMg/pSH3TYhzzkwur"
            + "HNhvezYuuYACkMWUeRWiHBhEeAsb86o/BjCnCpHC5pB9VEnIEpdERVAF+UEaVkYWWcqSqAiqcLsiC1de4yzCpNMMDT3lRYuJjKIs"
            + "cgUutJy0vKhBpm74wJvYWV7sEBM1YtwsushmjQMLIYvyBmTL5oWFljdg2xZBxdsWWsxwzZt0OtGHGZpqkpcxJjKKssgbuNBy0vLy"
            + "Bpmq5QNvYmd5wUNMzIpxs+gi2zcOLIQsyhuQ7ZsXFlregO1bBBVvW2gxw7Vv0uUDH2ZgfllexHjAIMIiV2AiiynLixpiWYaNu4WN"
            + "5QUOsAgjhc0hi2zUGKgIqiA/QLZoPlRkKQO2Z2FQuF2RhQvXmEmWCD/12yrgWKtpp+vuKbQ31U3ibkx107i7Ut00zpbU+tqoVvXW"
            + "PY3X+qRf57jIjUQcYdcuYdlueDOmPfYczfNoOpd3RDpNfCGcw3zuJ+Uyj0inmS+Ec5hPhSKXeFg4zfshS6Ndd6aA7kv9R5fW/vJ6"
            + "vMvMs5e4rdw950bPx2oZ3t3D7CTpC1OfeFKE9k6P/45Hnp1N7a1WH2PERau2v28Hf1qHjlVWr19PdXtVVf9UqX5oVemxyND7N3bf"
            + "VN5ZOrKgfdTLFqSfpKNLpnTSz9HRJVM6yafoyIJejc4ZuimUfMFIPLXbIkWTu41TNLnTTo2pvYdgphjrdMlJXU2rdzqo6k9RhrLn"
            + "TeTkypvKyYw3lZ2Hk2o/DrX6QF6qzcCk3/6YAM269ZKBKSQqvRaaA4qjijOq+JZkL6r0UmMOKI4qzqriO369qMIreRmYMKI4k0ov"
            + "qf0BhV0qG0HMvVY2Apl7sWwEMvNq2R9E/zlEN/oWj/7yRjt39P3ErM8p/vJK8w7i0cTskYZHKnl3D0EmqMYMXRp/e3gnG/p4REoY"
            + "fCF9915oKPsEfAb58fNtZngyuIT4BianYpNpu1uMwDbPVCDNAMz2efg59Ddw+Bx0EfUtzA73eXfbAdjsmQqkGYAZPw8/h/4GPp+D"
            + "LqK+hdnhPu+sSYKtnocvpA+zfBZ8BvkN3D0DXEJ8A5Nn+7pWp4N1g421KsAdrnCkXToh4fTghSyaVIo8M4gEBxInrHVD0SHUqTsK"
            + "kOBA4lvZnLrNIAucttsAB51LOmOTDA5aThprZubGjjis9KAZFB1JXewi0kNneeiYWlB4+EwCvpndMRWh7CAaEDubNrwuFJ3zyoAG"
            + "mxpaHUrPJUHRkdTFXiI9o5SHjqkOhWeVJOCb2R1THcrOLQGxs2nDq0PRsaAMaLCpodWh8BgLEhxIXOwiwiMtWeCYmlB2tEWAvZXN"
            + "MdWg6JgLDjqXNLwOlJwj4SNjzSysALe4GG0LHb3B2ERH+Oq1DVRIczHKb/01gjqElppwN3Yqio6cbEy4G5cNpg5yNjpv1KlQ17o6"
            + "WTezrw9IZC+WpIXD1ZIle6qHt1JnKvYIkxUz1mgSkimV26zQiLFxtFntsxwcQZzf/RRj42hvZG9+35OOze0TCZEzKfPPWaOAQYRx"
            + "niHpKBOBwWWQfew6E3cLG4OLXv5YRALLIotbqJODA4lLnQO3SscAhxRE2BodG3srm0MKJWqBTgqdSxrcJEqujMwFBvoHuFUU3R0J"
            + "Q0ZR3sTO6EIIbRoRy7YeWNyqrRwcSFzqH7glWwY4pDTCFmzZ2FvZHFIsUau1Uuhc0uC2UXLdZC4w0D/AbaPo3kkYMoryJnZGF0Jo"
            + "24hYw/fAwpbwxdg42lLngK3f07EhBRG1es+F3sjekOIIWroXImdSBjeIgqsqM3FxngFuDSV3VqKAQYS3sDG46EEbQsBWjgn1sZJc"
            + "1p/3heTxr3kdefwre6lagE9ZpibAU5baTepN8bfJwMNAm/AX2X+xD4HgP3z+PPxtMiCxPwteRv/7AwdO2AJyENYAMtFGZQy2kUyk"
            + "Af2dszaRURTItpCJNIhsBNs+JtKQkYXF9jSnuYyW5o00gLOAaBFY+PzayAuP7FFkK5B9AWh1x9Ow9UdAOSmyOvUqgFanPA0yG23T"
            + "t8tWsH0Wcr7zJv3HXPyNTCQrCczel8xGW1WpW3Uhs88JbGQmmC9tVK0yu6kyK21Vc/N6whvlQfalFz3hjapuggZYFjaql7YakGQf"
            + "Z9koD7Dv8O2s8EEPSQc8Gxt+bGSxXhwr2mhswtMgy8JGnUqeBlgWSJN65IaUqyOjrc4/B0etnLY4aQdTsZWd+HX4BocFURriRlqd"
            + "RryfQbSfeToVdVv02g3hngukSDmrakXbWz8PjepXd0oWXaOrTq+v4+7bwgD67/G3BeaNoRyJebsMR2JoWMmnZciIwFm111JVp/21"
            + "Lrp+aK33ANzov+PR/1lF67Za/ixafS5v9l2e51L3x4vvGa75UbBDUy8/3XloK3Vcvz1RFr0na+uE88ehpJw/CiXlZF1vwvdSdctC"
            + "8V6XbomYAtdHdd9bVa0LyT0EeeBODmkXLz+i7BQEAJNGU7YhFYBJoynaIySHDJHcYnZHjB1uGQ30H12uA25Z7/CRBe3X4mxB+jt8"
            + "dMmUTvo7fHTJlE7yO3xkQa9G5x2+KdT7mt0UMzrZ4U2N+7VCj9eF0jnv14USOk/YhRLar9jN6dbv7b23WleZLkuVdC1rSXKcliya"
            + "1MpxW7JoUivDcamSfp0e1x2Dre9/GUvH2JXwPB5lxd0icatnoy7T06x7XY1dsHv9ec/Dqf6slkmL47J7UlRnk0d9OpguXKfL8GOr"
            + "j4SPZ5990de6625JkFB8W1fIBx+JePRX1CKAWU+9EfEEBKUPPFIBMRQxRhQ/6uggSh90pAJiKGKsKH7E0UEUPuBIxIMQxJhQ+mjj"
            + "BLgeYU5BWzzHhgK2BxpeXMh7WjBkEmXIc0gwZBJlxIs2KGCb8P8O1XtpugCDGbhWI65+HfvTT0vHC5aLhPDsA7nS8+fIlZ5MkxIG"
            + "dnEyEN0HfLIA6dVYDqSAZFbdnYEIogiyYwCSTVLaYcyBRJEUmTKNKaGJcsokJIokypYov5R2wXMgUSRFtkxjSmii/DIJiSKJsiXK"
            + "L4WDmgxEEEWRIZOQApIol0whgiiC7CjxR/dt+6lf8P22/YTBIOjCzfU5Dm8uhzi8yYy5cKVWf3S3PxVdrwzw6+8n8yU680tXPWWI"
            + "lAcTGBBFUfyLcGIUIpUdxi47iF12ELvsEHZ5xtjlGWKXFwyZlzwy9btnUcOOvMUivzyRTamq1TrBPbxTTWlK/95wNpXmb48k7650"
            + "ilAg59D7qwGYcpqEGx8QoEyi/Is0pIBCgnIz8i/M8AJK75FFgAKI5tpTel9sChTkmJIrQLmAAFOCfFN6jyMCFEA016DS+xpToCDf"
            + "lFzBxwUEmBLkm8K71ACYcpq51hTemZbABLml4DIsJp7cjNk+GV6YvX+ZvNU9KLqIujvPAWSeAGcTz19OxUHnk4abmopMpOx21aC2"
            + "zoSXkQfZPA+dTx3u4IhtGQxovLnBPu52+aDmzoSXkQcZPQ+dTx3u44h9PAxovLnBPu70HqHWzkMXUQdZPAucTRzu3rhNXwRkuKlz"
            + "fbtUp5N1pjRKJJE+Dj+3Nwx8VyCuYC7sDAWuQFzBZHAGvpPehW+Kzjl6War2fVwHbKv9YxH6n0iCaXV1lUCr86Es+t76WJ1+v+qq"
            + "P6hrPVT9elGBKLPLkHnOkHlJyMzOwcyQV2iXI/ScI5TK0+yPzDx5hXY5Qs85Qqk8TWWAmSWfzC5D5jlDxsrPsR9L2XpQaEpsXX+k"
            + "1t8t2e/edpbwdzcmS/jeTBBlxzkbUyMNOusQH1PcPmnmF6cf5+PK0/TTD/Zx5Wn6yUf8mOIR7c5hv0Xce6m+9GlxaDV0MpUi45xS"
            + "pQg5J1YpQvbp1YWMdYZxjsk+e80U93+F/HPYXHmafm4ZyD+Z7ZdnloHsU9oL8UAZmOKYZSAuEygDcaFAGYgL+cvAJDNO6ldGyL4c"
            + "ZJHEe0x9EU84rB5PHTME5eB6PHkk804FUKpjqO/9iHO73UV5O9TnQ3/Rhz+GgL7ZkY06rYKuuc3sNbeBvWY3rdfsRvWa3ZxesxvS"
            + "a24Tes1tPK/eZvPKajCvvKbyymskr6zm0aQOlPir22TW9TWYq0Wcm4dFpMt4EWnzu6p302tXWcWHI2t/Z48svRCxhAma6UWJJUzQ"
            + "TC5QHNmQXqdYXVX13tZ/Qtsuf6JbQ6bbR2dsImnt2ZqfpMb5T56toJ74WyJ+tSH0qsu6sn+bhllf9+MW0F//hON+R+JWI+fxBgtn"
            + "wmkKdI08XXaxuBFjsXd0nHCMm5Uh7NjZlcXdL4YEzyfOfc4LhsylLLsmDYouoI42d/4r2ylk2X1vUHQBdbS9819uTSGLLq5DgucT"
            + "Rxs7+3XA63Ba/5rvQGKeUyDKeZQ7YulWiiAUVNStA0+3g9WX8PQQgsnWHYGhu5i469yGj3dc3XtJ8zW54xrf/QLv8c/7Bd7jn49L"
            + "l/U9IQp2cdluGnVKLOR6O+qyGK77rqo/b+6Xq7QZZI+Dk2U/pqp7fb9FrKiMyw7jxP7r8WIGLE9j3Ouv8MR3rT5W116czeCjpV3Y"
            + "ERVNXNcRlU1c1hGVjV/VYYvSbyeJSTLzyriXJCaazmnT6q4b2umsVa/34bRvXXEq1LIPXremsOZN0jBE7YGPK0ofY3Jk03rpI0yO"
            + "bFoveXzJEA1odQY+93DytE0qvTPpkRJwJkJSAvbkyD39ak730KhKzxfWzjbxV8uBW3DdsoJR8WhXuBoWTcusbNYQf9jAamVQKsKt"
            + "412DdyLtO24oi8YNtmbY6n/1TR9Oquhuq1BTYX1NF1eOk9GH48WQWN8RP/ZpumLcQBTvC+VDOXmOId3voYewimOxaN0vu4fQimOx"
            + "aM1X5ENYRaHCpIb+jraIbUy3y8StL1u9B64HAY0q9eFi/Hm8Qn8ceOz7onGJ+5PZpKZU3gmsR8xcme/nAcOyext++tZ7FTcBMfz6"
            + "IwDRehMGi/ggaT3+EUJ8dOhDD7FvgEbNbhwOnFf+9/Xh+b6ugJ7EWRJ48PxOj7wAM/zAkxC0XyWTEvTi8QkGXynO4xeHs1/RycQT"
            + "2A9Uo5IBRTmGlGFQjU8GROUY85FB9UwKEZVpQdWVAhRlGlN5pQD5FL2v9Al8O/zqnxwQU/oiL5iFEccZKuktXblA3i62D2eHIrQD"
            + "EXpGEXoGEXpBEXoBEfoLReivXELeC7Pc6Fs8erU00ui6ugW25i3inK15TVF9ZM3ikgXt2UVbkD6DS5dM6aTP3tIlUzrJM7dkQa9G"
            + "Z9Z2CjVBRm71re4P9fmOntBEdnyRZ77IS0JkdgJeZnwyuwyZ5wyZVH5mB+Plxyezy5B5zpBJ5WfyXl52PCI7vsgzX8TNiz0H3RTj"
            + "zoDDWPLWh/QudfvdBsxziX1x/FhX4K7o/TBcluz90FmW7Hy+iyBan77qMrTU3NRFNT7rdGqLZtovse8vBqHSXfd6boeuH65Pf3Rr"
            + "UFV5OBWtPvZFXY2PocabWwis0/hGUPuiQROlQboky6K7GEhVnXS3flL0J+5NdarsfR0IX4pbMsWXN8XPU8HRSOYWFi5A0D4LedwO"
            + "SCi6gDp33xIOmk1atgsSCy8hDzd5/kbIJLRsJyQWXkIebvP8zZBJaNFuSCi6gDrc4NkbIj3Ij1eDw8stj51x/l0M9tQeUMtjIwNM"
            + "CedtZaCW8CvLUSXBJZLUR9lQCzIr8K/C0yLPijVRu1FWCFqgWaF8Fk6JxD13vpEaZ00GqCavEoO94r6NFoDBptWJ7T9/UI3cZPbC"
            + "1EaZoajJzExkhQiZGaYaQmb64XxehTelakNT/FbkLRZpjcv7carBsC+Gqyf8a9zFPd7Z0buRb0V7vHw/m+GJHm/6qE2S4uRGHtWx"
            + "Hzo3/DTOUJTF6ljmd5RWBnToLm6Uf6/mPdLkv/EEt5ONrVm4Oapr2uGowzn7vBS99gh/nnR7GCe1lqHDtfkoKjfkcV70P6sPMrRN"
            + "mbf/nSHqzPA7oozVE4ZsWi9jBYUhm9ZLX0Whiwa0OispoyvmfHCqnM3DkqN/arJgQiP9I5MFExrJn5cq59Pn/bDHujq2ejXf2c5b"
            + "32mHHaKJnZMO0dTOMYdoavuMQzttNr6fPZsPfdlH2paHENwN/PEDaAj4yOEGLDyQvOdsHpI84egfAH0bw6fPQjLBFwdjfC4p5Z6A"
            + "34A8zu5x9Azq0bM8crun4GWWCaCDymoCHUZ9A3dPoGdQf2zmk1bvnRk7eIKmJVQr3Hs8zDciGMOsg2EmaD7Fbx+K+Yko1bXZl4Wf"
            + "ppXEU7LmFNOFav04flr17+KwESGGorlfx9X0kGKomjt0XFUPKYaqqSvH1fQj5CoqqrNJGVjSbnVnhniOh9SdnoawgQ11Vryzqc7y"
            + "cJ93/4QhV7ARoLYBQ5iypV8IKpWqbMUUgkqlKlpoRIA6RI9D+XHodNXV7XxBwOuvp2XgobmoTr8WlTr2xZ/EVloRmkNNq0NTHD9K"
            + "vZ//6153CfUpCY+K93Z97+e0bepwam8HJ+ZDl3rcatV9DGW5vk3hqa17NW0B+r26rowo88KX2fFF/l6JXIt+fJf80Ku3clVzXesP"
            + "64p6b229Thdpq9YJIy3NOmG4nTDp6v5y3+Tkhnur43uUHVzVn/tS3XTbrXctLiN2oYjnUMRLKOKvUMTfoYj/hCL+245wWjlj6fJw"
            + "Llpth5Wq6uc3FKaDx5Q98kQ5p3z5xNLvetPEvMrspnmeQva/cDa1+09jIdCv06uClOflMxDdx0myAOdnBaEcF5ACklP/CGpHPyKI"
            + "IsiOAUg2yUWXCOWQSUgUSZEp05gSmiinTEKiSKJsifLLRf8X5ZdJSBRJkS3TmBKaKL9MQqJIomyJ8svHaAfllilEEEWRIZOQApIo"
            + "l0whgiiC7Cjxx7OuzL/v46V6y35BUU1TV3daDIYu3lyhAwHnkggEnCyZjSc9+J0H4wwZ/Cg7DJkdhMwzhsxzHhnvPic78haL/PJE"
            + "NmZwt7pH9juceGHlPfk0T9wV7xX9JUeyZMAgjiDhdUe6KFUp4cVHuihVafoVSLJkSKX/fQEn9haNXTucPeMzLxllXQEbl01cAhsX"
            + "TlwDGxeOXwQ7y5JL1pj6ONivVCaSOPtfPGmcXS+eNPZel3EK+zNrTxZd0t5B5EjS92UxRJNa6XuzGKJJreT9WXRJj87CdL1OB3U0"
            + "kirUxIRS3Uipvrypwpdz+JPc0knCikLV1XeaeAsbSkXD+oqlCtWywWQ3WrK10k+te+MfbXubV1LHHbarCfNxq7Z3nWWKmAIDy7N2"
            + "Amd9th/fCe6/F6BWMb4NB33V74eqmxZC3BpxWode/m7VlH/BlbN8CLvB9CIwr5jNwCDRYF4pm4FBosG7QpYP4ZIolOn2NOpzfHuv"
            + "NhH10H3fv7CMG1dv+7EFL/px/e08rorOizoWXPNZtHqvjLMaVt91qunQq/b68zOwY2e9Ari6vtm3jwyvLLJHKFeZ5413J2PbKwNl"
            + "zNl7tmXGuMrS7jEh/v/YMKgKlavDpa4/bDOSBgQUhMSwgAKRGBxQIOJDhADCZGCJIZYAeXZYIuSZYYmQsMJwPrsv3PxRQ7luo9dV"
            + "+xR/r9K/t7X8Q5KfmLHE78OJfP1rAD6B+8gin8AagE9gHmXk61/J09QXvqcduKcqGCiRyjELhU+Fsr06CyWfisgo6fMLfgzuvu0s"
            + "lHwqbJvwzhYkMXhuIjmHkIUSzo/gzEISBGIUltcLDhqQQSJEpiHR4Whq0VNbV/tS/9HlesucN8XzOsW/09Cr6HT584qHGz8/7mFv"
            + "c1tHDuXbY8t/uK33C8XX/lYywB1lTFz68icBOGvpl4kLICzdI8UFxlLGGlm8fSiILN3xwwXGUsZaWbwZJogs3L/CxIUSxppYurvj"
            + "Dux7qGmO0v82dZdZzT8egkq0StbbU6n4XBYbtTp8BdzvQ9Mg8Cy+AmQWMC1ThoaNMrHRhwA1WjEVmNYrQ8NGmdjoS4AatpgKSAvH"
            + "V7BNFjb6DJjGL/5cYTAN7fhoUY2LVVOeY+teEC1t8qCiUI3xel9m6HYVqdkkM236xOhGesDZoZ1+3UJJbkZCx5jBRSaupt08O+hC"
            + "w9SzTXaAxYarCJ0hbMERnc2nKgmdqgeXnLiadvPsoEsOU8822QGWHK4idIawJUd0VQRVSeCWB3DBiWppt84Mutjw1GySGWChYeoB"
            + "ZwdbZCR3lsw6PrXqL7rlz6xNt8bm7CmnS9p7nh1J+p5yhmhSK31POUM0qZW8p5wu6dd5VNWp1Pv5v+719+P2kITPcqQdZwwLpy+6"
            + "4AiTFO9Eed5J8ryT5HknyPOzRPGzQPGLyNgvEmO/SPL8wsuzdZnuPdi9TneOIF+om0juHClKpHeOFyXS20eN5uRjI1XNt/GuziTc"
            + "rxr3nR7w30I+h04n4d7U2HmKm8GTMGAAT8pA1j0p/Zm2LlZ8BEKnbxCofhf1gApHzBBYMlnhIAUCSyYr6x0iUB2qRXma3/kkP4BL"
            + "E9nxRZ75Ii8JkeQDuESZXYbMc4ZMKj/JB3CJMrsMmecMmVR+Ug/g0kR2fJFnvsgqLzddlnXe6VqGqN1Vd0XpYyGObFovfTTEkU3r"
            + "JY+HGKIBrU7n7R5O7qSl0judlJSA01dJCdhdlnt6b1fsHjf1Z/4PVRueIsivAQA=";

    private Mc263ExactStateProductionClosureJ4Data() {}

    static int blockId(String key) {
        return switch (key) {
            case "minecraft:acacia_door" -> com.gameexpert.terrain.Blocks.ACACIA_DOOR;
            case "minecraft:acacia_fence_gate" -> com.gameexpert.terrain.Blocks.ACACIA_FENCE_GATE;
            case "minecraft:acacia_log" -> com.gameexpert.terrain.Blocks.ACACIA_LOG;
            case "minecraft:acacia_planks" -> com.gameexpert.terrain.Blocks.ACACIA_PLANK;
            case "minecraft:acacia_pressure_plate" -> com.gameexpert.terrain.Blocks.OAK_PRESSURE_PLATE;
            case "minecraft:acacia_sapling" -> com.gameexpert.terrain.Blocks.ACACIA_SAPLING;
            case "minecraft:acacia_wood" -> com.gameexpert.terrain.Blocks.ACACIA_WOOD;
            case "minecraft:air" -> com.gameexpert.terrain.Blocks.AIR;
            case "minecraft:allium" -> com.gameexpert.terrain.Blocks.ALLIUM;
            case "minecraft:attached_melon_stem" -> com.gameexpert.terrain.Blocks.MELON_STEM;
            case "minecraft:attached_pumpkin_stem" -> com.gameexpert.terrain.Blocks.PUMPKIN_STEM;
            case "minecraft:azure_bluet" -> com.gameexpert.terrain.Blocks.AZURE_BLUET;
            case "minecraft:bell" -> com.gameexpert.terrain.Blocks.BELL;
            case "minecraft:birch_leaves" -> com.gameexpert.terrain.Blocks.BIRCH_LEAVES;
            case "minecraft:birch_log" -> com.gameexpert.terrain.Blocks.BIRCH_LOG;
            case "minecraft:birch_planks" -> com.gameexpert.terrain.Blocks.BIRCH_PLANK;
            case "minecraft:birch_slab" -> com.gameexpert.terrain.Blocks.BIRCH_SLAB;
            case "minecraft:black_bed" -> com.gameexpert.terrain.Blocks.BLACK_BED;
            case "minecraft:black_carpet" -> com.gameexpert.terrain.Blocks.BLACK_CARPET;
            case "minecraft:black_glazed_terracotta" -> com.gameexpert.terrain.Blocks.BLACK_GLAZED_TERRACOTTA;
            case "minecraft:black_stained_glass" -> com.gameexpert.terrain.Blocks.BLACK_STAINED_GLASS;
            case "minecraft:black_wall_banner" -> com.gameexpert.terrain.Blocks.WHITE_WALL_BANNER;
            case "minecraft:black_wool" -> com.gameexpert.terrain.Blocks.BLACK_WOOL;
            case "minecraft:blast_furnace" -> com.gameexpert.terrain.Blocks.BLAST_FURNACE;
            case "minecraft:blue_bed" -> com.gameexpert.terrain.Blocks.BLUE_BED;
            case "minecraft:blue_carpet" -> com.gameexpert.terrain.Blocks.BLUE_CARPET;
            case "minecraft:blue_ice" -> com.gameexpert.terrain.Blocks.BLUE_ICE;
            case "minecraft:blue_orchid" -> com.gameexpert.terrain.Blocks.BLUE_ORCHID;
            case "minecraft:blue_terracotta" -> com.gameexpert.terrain.Blocks.BLUE_TERRACOTTA;
            case "minecraft:blue_wool" -> com.gameexpert.terrain.Blocks.BLUE_WOOL;
            case "minecraft:bone_block" -> com.gameexpert.terrain.Blocks.BONE_BLOCK;
            case "minecraft:bookshelf" -> com.gameexpert.terrain.Blocks.BOOKSHELF;
            case "minecraft:brewing_stand" -> com.gameexpert.terrain.Blocks.BREWING_STAND;
            case "minecraft:brick_slab" -> com.gameexpert.terrain.Blocks.BRICK_SLAB;
            case "minecraft:brick_stairs" -> com.gameexpert.terrain.Blocks.BRICK_STAIRS;
            case "minecraft:bricks" -> com.gameexpert.terrain.Blocks.BRICKS;
            case "minecraft:brown_bed" -> com.gameexpert.terrain.Blocks.BROWN_BED;
            case "minecraft:brown_carpet" -> com.gameexpert.terrain.Blocks.BROWN_CARPET;
            case "minecraft:brown_mushroom_block" -> com.gameexpert.terrain.Blocks.BROWN_MUSHROOM_BLOCK;
            case "minecraft:brown_stained_glass" -> com.gameexpert.terrain.Blocks.BROWN_STAINED_GLASS;
            case "minecraft:brown_stained_glass_pane" -> com.gameexpert.terrain.Blocks.BROWN_STAINED_GLASS_PANE;
            case "minecraft:brown_terracotta" -> com.gameexpert.terrain.Blocks.BROWN_TERRACOTTA;
            case "minecraft:brown_wall_banner" -> com.gameexpert.terrain.Blocks.BROWN_WALL_BANNER;
            case "minecraft:brown_wool" -> com.gameexpert.terrain.Blocks.BROWN_WOOL;
            case "minecraft:bush" -> com.gameexpert.terrain.Blocks.BUSH;
            case "minecraft:cactus" -> com.gameexpert.terrain.Blocks.CACTUS;
            case "minecraft:cactus_flower" -> com.gameexpert.terrain.Blocks.CACTUS_FLOWER;
            case "minecraft:cartography_table" -> com.gameexpert.terrain.Blocks.CARTOGRAPHY_TABLE;
            case "minecraft:carved_pumpkin" -> com.gameexpert.terrain.Blocks.CARVED_PUMPKIN;
            case "minecraft:cauldron" -> com.gameexpert.terrain.Blocks.CAULDRON;
            case "minecraft:cherry_log" -> com.gameexpert.terrain.Blocks.CHERRY_LOG;
            case "minecraft:cherry_sapling" -> com.gameexpert.terrain.Blocks.CHERRY_SAPLING;
            case "minecraft:chiseled_deepslate" -> com.gameexpert.terrain.Blocks.CHISELED_DEEPSLATE;
            case "minecraft:chiseled_sandstone" -> com.gameexpert.terrain.Blocks.CHISELED_SANDSTONE;
            case "minecraft:chiseled_stone_bricks" -> com.gameexpert.terrain.Blocks.CHISELED_STONE_BRICKS;
            case "minecraft:chiseled_tuff" -> com.gameexpert.terrain.Blocks.CHISELED_TUFF;
            case "minecraft:chiseled_tuff_bricks" -> com.gameexpert.terrain.Blocks.CHISELED_TUFF_BRICKS;
            case "minecraft:clay" -> com.gameexpert.terrain.Blocks.CLAY;
            case "minecraft:closed_eyeblossom" -> com.gameexpert.terrain.Blocks.CLOSED_EYEBLOSSOM;
            case "minecraft:coal_block" -> com.gameexpert.terrain.Blocks.COAL_BLOCK;
            case "minecraft:coal_ore" -> com.gameexpert.terrain.Blocks.COAL_ORE;
            case "minecraft:coarse_dirt" -> com.gameexpert.terrain.Blocks.COARSE_DIRT;
            case "minecraft:cobbled_deepslate" -> com.gameexpert.terrain.Blocks.COBBLED_DEEPSLATE;
            case "minecraft:cobbled_deepslate_slab" -> com.gameexpert.terrain.Blocks.POLISHED_DEEPSLATE_SLAB;
            case "minecraft:cobbled_deepslate_stairs" -> com.gameexpert.terrain.Blocks.POLISHED_DEEPSLATE_STAIRS;
            case "minecraft:cobbled_deepslate_wall" -> com.gameexpert.terrain.Blocks.POLISHED_DEEPSLATE_WALL;
            case "minecraft:cobblestone_stairs" -> com.gameexpert.terrain.Blocks.COBBLE_STAIRS;
            case "minecraft:cobweb" -> com.gameexpert.terrain.Blocks.COBWEB;
            case "minecraft:cocoa" -> com.gameexpert.terrain.Blocks.COCOA;
            case "minecraft:comparator" -> com.gameexpert.terrain.Blocks.REPEATER;
            case "minecraft:composter" -> com.gameexpert.terrain.Blocks.COMPOSTER;
            case "minecraft:copper_block" -> com.gameexpert.terrain.Blocks.COPPER_BLOCK;
            case "minecraft:cornflower" -> com.gameexpert.terrain.Blocks.CORNFLOWER;
            case "minecraft:cracked_deepslate_bricks" -> com.gameexpert.terrain.Blocks.CRACKED_DEEPSLATE_BRICKS;
            case "minecraft:cracked_deepslate_tiles" -> com.gameexpert.terrain.Blocks.CRACKED_DEEPSLATE_TILES;
            case "minecraft:cracked_stone_bricks" -> com.gameexpert.terrain.Blocks.CRACKED_STONE_BRICKS;
            case "minecraft:crafting_table" -> com.gameexpert.terrain.Blocks.CRAFTING_TABLE;
            case "minecraft:creaking_heart" -> com.gameexpert.terrain.Blocks.CREAKING_HEART;
            case "minecraft:cut_sandstone" -> com.gameexpert.terrain.Blocks.CUT_SANDSTONE;
            case "minecraft:cyan_bed" -> com.gameexpert.terrain.Blocks.CYAN_BED;
            case "minecraft:cyan_carpet" -> com.gameexpert.terrain.Blocks.CYAN_CARPET;
            case "minecraft:cyan_glazed_terracotta" -> com.gameexpert.terrain.Blocks.CYAN_GLAZED_TERRACOTTA;
            case "minecraft:cyan_terracotta" -> com.gameexpert.terrain.Blocks.CYAN_TERRACOTTA;
            case "minecraft:cyan_wool" -> com.gameexpert.terrain.Blocks.CYAN_WOOL;
            case "minecraft:damaged_anvil" -> com.gameexpert.terrain.Blocks.DAMAGED_ANVIL;
            case "minecraft:dark_oak_door" -> com.gameexpert.terrain.Blocks.DARK_OAK_DOOR;
            case "minecraft:dark_oak_fence_gate" -> com.gameexpert.terrain.Blocks.DARK_OAK_FENCE_GATE;
            case "minecraft:dark_oak_log" -> com.gameexpert.terrain.Blocks.DARK_OAK_LOG;
            case "minecraft:dark_oak_planks" -> com.gameexpert.terrain.Blocks.DARK_OAK_PLANK;
            case "minecraft:dark_oak_sapling" -> com.gameexpert.terrain.Blocks.DARK_OAK_SAPLING;
            case "minecraft:dark_oak_slab" -> com.gameexpert.terrain.Blocks.DARK_OAK_SLAB;
            case "minecraft:dark_oak_trapdoor" -> com.gameexpert.terrain.Blocks.DARK_OAK_TRAPDOOR;
            case "minecraft:dead_bush" -> com.gameexpert.terrain.Blocks.DEAD_BUSH;
            case "minecraft:deepslate_brick_slab" -> com.gameexpert.terrain.Blocks.DEEPSLATE_BRICK_SLAB;
            case "minecraft:deepslate_brick_stairs" -> com.gameexpert.terrain.Blocks.DEEPSLATE_BRICK_STAIRS;
            case "minecraft:deepslate_brick_wall" -> com.gameexpert.terrain.Blocks.DEEPSLATE_BRICK_WALL;
            case "minecraft:deepslate_bricks" -> com.gameexpert.terrain.Blocks.DEEPSLATE_BRICKS;
            case "minecraft:deepslate_diamond_ore" -> com.gameexpert.terrain.Blocks.DEEPSLATE_DIAMOND_ORE;
            case "minecraft:deepslate_tile_slab" -> com.gameexpert.terrain.Blocks.DEEPSLATE_TILE_SLAB;
            case "minecraft:deepslate_tile_stairs" -> com.gameexpert.terrain.Blocks.DEEPSLATE_TILE_STAIRS;
            case "minecraft:deepslate_tile_wall" -> com.gameexpert.terrain.Blocks.DEEPSLATE_TILE_WALL;
            case "minecraft:deepslate_tiles" -> com.gameexpert.terrain.Blocks.DEEPSLATE_TILES;
            case "minecraft:diamond_block" -> com.gameexpert.terrain.Blocks.DIAMOND_BLOCK;
            case "minecraft:diorite" -> com.gameexpert.terrain.Blocks.DIORITE;
            case "minecraft:diorite_slab" -> com.gameexpert.terrain.Blocks.DIORITE_SLAB;
            case "minecraft:dirt" -> com.gameexpert.terrain.Blocks.DIRT;
            case "minecraft:dirt_path" -> com.gameexpert.terrain.Blocks.DIRT_PATH;
            case "minecraft:dispenser" -> com.gameexpert.terrain.Blocks.DISPENSER;
            case "minecraft:farmland" -> com.gameexpert.terrain.Blocks.FARMLAND;
            case "minecraft:fern" -> com.gameexpert.terrain.Blocks.FERN;
            case "minecraft:firefly_bush" -> com.gameexpert.terrain.Blocks.FIREFLY_BUSH;
            case "minecraft:fletching_table" -> com.gameexpert.terrain.Blocks.FLETCHING_TABLE;
            case "minecraft:flower_pot" -> com.gameexpert.terrain.Blocks.FLOWER_POT;
            case "minecraft:furnace" -> com.gameexpert.terrain.Blocks.FURNACE;
            case "minecraft:glass" -> com.gameexpert.terrain.Blocks.GLASS;
            case "minecraft:gold_block" -> com.gameexpert.terrain.Blocks.GOLD_BLOCK;
            case "minecraft:gold_ore" -> com.gameexpert.terrain.Blocks.GOLD_ORE;
            case "minecraft:granite" -> com.gameexpert.terrain.Blocks.GRANITE;
            case "minecraft:granite_stairs" -> com.gameexpert.terrain.Blocks.GRANITE_STAIRS;
            case "minecraft:granite_wall" -> com.gameexpert.terrain.Blocks.GRANITE_WALL;
            case "minecraft:gravel" -> com.gameexpert.terrain.Blocks.GRAVEL;
            case "minecraft:gray_bed" -> com.gameexpert.terrain.Blocks.GRAY_BED;
            case "minecraft:gray_carpet" -> com.gameexpert.terrain.Blocks.GRAY_CARPET;
            case "minecraft:gray_terracotta" -> com.gameexpert.terrain.Blocks.GRAY_TERRACOTTA;
            case "minecraft:gray_wall_banner" -> com.gameexpert.terrain.Blocks.WHITE_WALL_BANNER;
            case "minecraft:gray_wool" -> com.gameexpert.terrain.Blocks.GRAY_WOOL;
            case "minecraft:green_bed" -> com.gameexpert.terrain.Blocks.GREEN_BED;
            case "minecraft:green_carpet" -> com.gameexpert.terrain.Blocks.GREEN_CARPET;
            case "minecraft:green_wool" -> com.gameexpert.terrain.Blocks.GREEN_WOOL;
            case "minecraft:hay_block" -> com.gameexpert.terrain.Blocks.HAY_BLOCK;
            case "minecraft:hopper" -> com.gameexpert.terrain.Blocks.HOPPER;
            case "minecraft:ice" -> com.gameexpert.terrain.Blocks.ICE;
            case "minecraft:infested_chiseled_stone_bricks" -> com.gameexpert.terrain.Blocks.INFESTED_CHISELED_STONE_BRICKS;
            case "minecraft:infested_cobblestone" -> com.gameexpert.terrain.Blocks.INFESTED_STONE;
            case "minecraft:infested_mossy_stone_bricks" -> com.gameexpert.terrain.Blocks.INFESTED_MOSSY_STONE_BRICKS;
            case "minecraft:infested_stone_bricks" -> com.gameexpert.terrain.Blocks.INFESTED_STONE_BRICKS;
            case "minecraft:iron_door" -> com.gameexpert.terrain.Blocks.IRON_DOOR;
            case "minecraft:iron_ore" -> com.gameexpert.terrain.Blocks.IRON_ORE;
            case "minecraft:iron_trapdoor" -> com.gameexpert.terrain.Blocks.IRON_TRAPDOOR;
            case "minecraft:jungle_button" -> com.gameexpert.terrain.Blocks.OAK_BUTTON;
            case "minecraft:jungle_door" -> com.gameexpert.terrain.Blocks.JUNGLE_DOOR;
            case "minecraft:jungle_fence_gate" -> com.gameexpert.terrain.Blocks.JUNGLE_FENCE_GATE;
            case "minecraft:jungle_leaves" -> com.gameexpert.terrain.Blocks.JUNGLE_LEAVES;
            case "minecraft:jungle_log" -> com.gameexpert.terrain.Blocks.JUNGLE_LOG;
            case "minecraft:jungle_planks" -> com.gameexpert.terrain.Blocks.JUNGLE_PLANK;
            case "minecraft:jungle_sapling" -> com.gameexpert.terrain.Blocks.JUNGLE_SAPLING;
            case "minecraft:jungle_slab" -> com.gameexpert.terrain.Blocks.JUNGLE_SLAB;
            case "minecraft:jungle_stairs" -> com.gameexpert.terrain.Blocks.JUNGLE_STAIRS;
            case "minecraft:jungle_trapdoor" -> com.gameexpert.terrain.Blocks.JUNGLE_TRAPDOOR;
            case "minecraft:ladder" -> com.gameexpert.terrain.Blocks.LADDER;
            case "minecraft:lapis_block" -> com.gameexpert.terrain.Blocks.LAPIS_ORE;
            case "minecraft:large_fern" -> com.gameexpert.terrain.Blocks.LARGE_FERN;
            case "minecraft:leaf_litter" -> com.gameexpert.terrain.Blocks.LEAF_LITTER;
            case "minecraft:lectern" -> com.gameexpert.terrain.Blocks.LECTERN;
            case "minecraft:light_blue_bed" -> com.gameexpert.terrain.Blocks.LIGHT_BLUE_BED;
            case "minecraft:light_blue_carpet" -> com.gameexpert.terrain.Blocks.LIGHT_BLUE_CARPET;
            case "minecraft:light_blue_glazed_terracotta" -> com.gameexpert.terrain.Blocks.LIGHT_BLUE_GLAZED_TERRACOTTA;
            case "minecraft:light_blue_wool" -> com.gameexpert.terrain.Blocks.LIGHT_BLUE_WOOL;
            case "minecraft:light_gray_bed" -> com.gameexpert.terrain.Blocks.LIGHT_GRAY_BED;
            case "minecraft:light_gray_carpet" -> com.gameexpert.terrain.Blocks.LIGHT_GRAY_CARPET;
            case "minecraft:light_gray_glazed_terracotta" -> com.gameexpert.terrain.Blocks.LIGHT_GRAY_GLAZED_TERRACOTTA;
            case "minecraft:light_gray_stained_glass" -> com.gameexpert.terrain.Blocks.LIGHT_GRAY_STAINED_GLASS;
            case "minecraft:light_gray_terracotta" -> com.gameexpert.terrain.Blocks.LIGHT_GRAY_TERRACOTTA;
            case "minecraft:light_gray_wall_banner" -> com.gameexpert.terrain.Blocks.WHITE_WALL_BANNER;
            case "minecraft:light_gray_wool" -> com.gameexpert.terrain.Blocks.LIGHT_GRAY_WOOL;
            case "minecraft:lilac" -> com.gameexpert.terrain.Blocks.LILAC;
            case "minecraft:lily_of_the_valley" -> com.gameexpert.terrain.Blocks.LILY_OF_THE_VALLEY;
            case "minecraft:lily_pad" -> com.gameexpert.terrain.Blocks.LILY_PAD;
            case "minecraft:lime_bed" -> com.gameexpert.terrain.Blocks.LIME_BED;
            case "minecraft:lime_carpet" -> com.gameexpert.terrain.Blocks.LIME_CARPET;
            case "minecraft:lime_glazed_terracotta" -> com.gameexpert.terrain.Blocks.LIME_GLAZED_TERRACOTTA;
            case "minecraft:lime_terracotta" -> com.gameexpert.terrain.Blocks.LIME_TERRACOTTA;
            case "minecraft:lime_wool" -> com.gameexpert.terrain.Blocks.LIME_WOOL;
            case "minecraft:loom" -> com.gameexpert.terrain.Blocks.LOOM;
            case "minecraft:magenta_bed" -> com.gameexpert.terrain.Blocks.MAGENTA_BED;
            case "minecraft:magenta_carpet" -> com.gameexpert.terrain.Blocks.MAGENTA_CARPET;
            case "minecraft:mangrove_log" -> com.gameexpert.terrain.Blocks.MANGROVE_LOG;
            case "minecraft:mangrove_roots" -> com.gameexpert.terrain.Blocks.MANGROVE_ROOTS;
            case "minecraft:mangrove_wood" -> com.gameexpert.terrain.Blocks.MANGROVE_WOOD;
            case "minecraft:melon" -> com.gameexpert.terrain.Blocks.MELON;
            case "minecraft:melon_stem" -> com.gameexpert.terrain.Blocks.MELON_STEM;
            case "minecraft:moss_block" -> com.gameexpert.terrain.Blocks.MOSS_BLOCK;
            case "minecraft:moss_carpet" -> com.gameexpert.terrain.Blocks.MOSS_CARPET;
            case "minecraft:mossy_cobblestone_slab" -> com.gameexpert.terrain.Blocks.MOSSY_COBBLE_SLAB;
            case "minecraft:mossy_cobblestone_stairs" -> com.gameexpert.terrain.Blocks.MOSSY_COBBLE_STAIRS;
            case "minecraft:mud" -> com.gameexpert.terrain.Blocks.MUD;
            case "minecraft:mud_brick_slab" -> com.gameexpert.terrain.Blocks.MUD_BRICK_SLAB;
            case "minecraft:mud_bricks" -> com.gameexpert.terrain.Blocks.MUD_BRICKS;
            case "minecraft:muddy_mangrove_roots" -> com.gameexpert.terrain.Blocks.MUDDY_MANGROVE_ROOTS;
            case "minecraft:mushroom_stem" -> com.gameexpert.terrain.Blocks.MUSHROOM_STEM;
            case "minecraft:mycelium" -> com.gameexpert.terrain.Blocks.MYCELIUM;
            case "minecraft:netherrack" -> com.gameexpert.terrain.Blocks.NETHERRACK;
            case "minecraft:note_block" -> com.gameexpert.terrain.Blocks.PLANK;
            case "minecraft:oak_button" -> com.gameexpert.terrain.Blocks.OAK_BUTTON;
            case "minecraft:oak_pressure_plate" -> com.gameexpert.terrain.Blocks.OAK_PRESSURE_PLATE;
            case "minecraft:obsidian" -> com.gameexpert.terrain.Blocks.OBSIDIAN;
            case "minecraft:orange_bed" -> com.gameexpert.terrain.Blocks.ORANGE_BED;
            case "minecraft:orange_carpet" -> com.gameexpert.terrain.Blocks.ORANGE_CARPET;
            case "minecraft:orange_glazed_terracotta" -> com.gameexpert.terrain.Blocks.ORANGE_GLAZED_TERRACOTTA;
            case "minecraft:orange_stained_glass_pane" -> com.gameexpert.terrain.Blocks.ORANGE_STAINED_GLASS_PANE;
            case "minecraft:orange_terracotta" -> com.gameexpert.terrain.Blocks.ORANGE_TERRACOTTA;
            case "minecraft:orange_tulip" -> com.gameexpert.terrain.Blocks.ORANGE_TULIP;
            case "minecraft:orange_wool" -> com.gameexpert.terrain.Blocks.ORANGE_WOOL;
            case "minecraft:oxeye_daisy" -> com.gameexpert.terrain.Blocks.OXEYE_DAISY;
            case "minecraft:oxidized_copper_chest" -> com.gameexpert.terrain.Blocks.OXIDIZED_COPPER_CHEST;
            case "minecraft:oxidized_cut_copper" -> com.gameexpert.terrain.Blocks.OXIDIZED_CUT_COPPER;
            case "minecraft:packed_ice" -> com.gameexpert.terrain.Blocks.PACKED_ICE;
            case "minecraft:packed_mud" -> com.gameexpert.terrain.Blocks.PACKED_MUD;
            case "minecraft:pale_hanging_moss" -> com.gameexpert.terrain.Blocks.PALE_HANGING_MOSS;
            case "minecraft:pale_moss_block" -> com.gameexpert.terrain.Blocks.PALE_MOSS_BLOCK;
            case "minecraft:pale_moss_carpet" -> com.gameexpert.terrain.Blocks.PALE_MOSS_CARPET;
            case "minecraft:pale_oak_leaves" -> com.gameexpert.terrain.Blocks.PALE_OAK_LEAVES;
            case "minecraft:pale_oak_log" -> com.gameexpert.terrain.Blocks.PALE_OAK_LOG;
            case "minecraft:peony" -> com.gameexpert.terrain.Blocks.PEONY;
            case "minecraft:pink_bed" -> com.gameexpert.terrain.Blocks.PINK_BED;
            case "minecraft:pink_carpet" -> com.gameexpert.terrain.Blocks.PINK_CARPET;
            case "minecraft:pink_petals" -> com.gameexpert.terrain.Blocks.PINK_PETALS;
            case "minecraft:pink_tulip" -> com.gameexpert.terrain.Blocks.PINK_TULIP;
            case "minecraft:piston_head" -> com.gameexpert.terrain.Blocks.STICKY_PISTON;
            case "minecraft:podzol" -> com.gameexpert.terrain.Blocks.PODZOL;
            case "minecraft:pointed_dripstone" -> com.gameexpert.terrain.Blocks.POINTED_DRIPSTONE;
            case "minecraft:polished_andesite" -> com.gameexpert.terrain.Blocks.POLISHED_ANDESITE;
            case "minecraft:polished_basalt" -> com.gameexpert.terrain.Blocks.SMOOTH_BASALT;
            case "minecraft:polished_deepslate" -> com.gameexpert.terrain.Blocks.POLISHED_DEEPSLATE;
            case "minecraft:polished_deepslate_slab" -> com.gameexpert.terrain.Blocks.POLISHED_DEEPSLATE_SLAB;
            case "minecraft:polished_deepslate_stairs" -> com.gameexpert.terrain.Blocks.POLISHED_DEEPSLATE_STAIRS;
            case "minecraft:polished_deepslate_wall" -> com.gameexpert.terrain.Blocks.POLISHED_DEEPSLATE_WALL;
            case "minecraft:polished_tuff" -> com.gameexpert.terrain.Blocks.POLISHED_TUFF;
            case "minecraft:poplar_log" -> com.gameexpert.terrain.Blocks.POPLAR_LOG;
            case "minecraft:potted_allium" -> com.gameexpert.terrain.Blocks.POTTED_RED_TULIP;
            case "minecraft:potted_azure_bluet" -> com.gameexpert.terrain.Blocks.POTTED_RED_TULIP;
            case "minecraft:potted_birch_sapling" -> com.gameexpert.terrain.Blocks.POTTED_POPLAR_SAPLING;
            case "minecraft:potted_blue_orchid" -> com.gameexpert.terrain.Blocks.POTTED_RED_TULIP;
            case "minecraft:potted_cactus" -> com.gameexpert.terrain.Blocks.POTTED_CACTUS;
            case "minecraft:potted_dandelion" -> com.gameexpert.terrain.Blocks.POTTED_RED_TULIP;
            case "minecraft:potted_dead_bush" -> com.gameexpert.terrain.Blocks.POTTED_DEAD_BUSH;
            case "minecraft:potted_oxeye_daisy" -> com.gameexpert.terrain.Blocks.POTTED_RED_TULIP;
            case "minecraft:potted_poppy" -> com.gameexpert.terrain.Blocks.POTTED_RED_TULIP;
            case "minecraft:potted_red_tulip" -> com.gameexpert.terrain.Blocks.POTTED_RED_TULIP;
            case "minecraft:potted_spruce_sapling" -> com.gameexpert.terrain.Blocks.POTTED_POPLAR_SAPLING;
            case "minecraft:potted_white_tulip" -> com.gameexpert.terrain.Blocks.POTTED_RED_TULIP;
            case "minecraft:powder_snow" -> com.gameexpert.terrain.Blocks.POWDER_SNOW;
            case "minecraft:pumpkin" -> com.gameexpert.terrain.Blocks.PUMPKIN;
            case "minecraft:pumpkin_stem" -> com.gameexpert.terrain.Blocks.PUMPKIN_STEM;
            case "minecraft:purple_bed" -> com.gameexpert.terrain.Blocks.PURPLE_BED;
            case "minecraft:purple_carpet" -> com.gameexpert.terrain.Blocks.PURPLE_CARPET;
            case "minecraft:red_bed" -> com.gameexpert.terrain.Blocks.RED_BED;
            case "minecraft:red_carpet" -> com.gameexpert.terrain.Blocks.RED_CARPET;
            case "minecraft:red_concrete" -> com.gameexpert.terrain.Blocks.RED_CONCRETE;
            case "minecraft:red_glazed_terracotta" -> com.gameexpert.terrain.Blocks.RED_GLAZED_TERRACOTTA;
            case "minecraft:red_mushroom_block" -> com.gameexpert.terrain.Blocks.RED_MUSHROOM_BLOCK;
            case "minecraft:red_sand" -> com.gameexpert.terrain.Blocks.RED_SAND;
            case "minecraft:red_sandstone" -> com.gameexpert.terrain.Blocks.RED_SANDSTONE;
            case "minecraft:red_terracotta" -> com.gameexpert.terrain.Blocks.RED_TERRACOTTA;
            case "minecraft:red_tulip" -> com.gameexpert.terrain.Blocks.RED_TULIP;
            case "minecraft:red_wool" -> com.gameexpert.terrain.Blocks.RED_WOOL;
            case "minecraft:redstone_block" -> com.gameexpert.terrain.Blocks.REDSTONE_ORE;
            case "minecraft:redstone_lamp" -> com.gameexpert.terrain.Blocks.GLOWSTONE;
            case "minecraft:redstone_wall_torch" -> com.gameexpert.terrain.Blocks.WALL_TORCH_N;
            case "minecraft:reinforced_deepslate" -> com.gameexpert.terrain.Blocks.DEEPSLATE;
            case "minecraft:resin_block" -> com.gameexpert.terrain.Blocks.RESIN_BLOCK;
            case "minecraft:rose_bush" -> com.gameexpert.terrain.Blocks.ROSE_BUSH;
            case "minecraft:sand" -> com.gameexpert.terrain.Blocks.SAND;
            case "minecraft:sandstone" -> com.gameexpert.terrain.Blocks.SANDSTONE;
            case "minecraft:sandstone_stairs" -> com.gameexpert.terrain.Blocks.SANDSTONE_STAIRS;
            case "minecraft:sculk_sensor" -> com.gameexpert.terrain.Blocks.SCULK_SENSOR;
            case "minecraft:sea_pickle" -> com.gameexpert.terrain.Blocks.SEA_PICKLE;
            case "minecraft:seagrass" -> com.gameexpert.terrain.Blocks.SEAGRASS;
            case "minecraft:short_dry_grass" -> com.gameexpert.terrain.Blocks.SHORT_DRY_GRASS;
            case "minecraft:skeleton_skull" -> com.gameexpert.terrain.Blocks.BONE_BLOCK;
            case "minecraft:smithing_table" -> com.gameexpert.terrain.Blocks.SMITHING_TABLE;
            case "minecraft:smoker" -> com.gameexpert.terrain.Blocks.SMOKER;
            case "minecraft:smooth_basalt" -> com.gameexpert.terrain.Blocks.SMOOTH_BASALT;
            case "minecraft:smooth_sandstone" -> com.gameexpert.terrain.Blocks.SMOOTH_SANDSTONE;
            case "minecraft:smooth_stone" -> com.gameexpert.terrain.Blocks.SMOOTH_STONE;
            case "minecraft:snow" -> com.gameexpert.terrain.Blocks.SNOW;
            case "minecraft:snow_block" -> com.gameexpert.terrain.Blocks.SNOW_BLOCK;
            case "minecraft:soul_fire" -> com.gameexpert.terrain.Blocks.FIRE;
            case "minecraft:soul_lantern" -> com.gameexpert.terrain.Blocks.LANTERN;
            case "minecraft:soul_sand" -> com.gameexpert.terrain.Blocks.SAND;
            case "minecraft:spruce_door" -> com.gameexpert.terrain.Blocks.SPRUCE_DOOR;
            case "minecraft:spruce_fence_gate" -> com.gameexpert.terrain.Blocks.SPRUCE_FENCE_GATE;
            case "minecraft:spruce_leaves" -> com.gameexpert.terrain.Blocks.SPRUCE_LEAVES;
            case "minecraft:spruce_log" -> com.gameexpert.terrain.Blocks.SPRUCE_LOG;
            case "minecraft:spruce_planks" -> com.gameexpert.terrain.Blocks.SPRUCE_PLANK;
            case "minecraft:spruce_pressure_plate" -> com.gameexpert.terrain.Blocks.OAK_PRESSURE_PLATE;
            case "minecraft:spruce_wall_sign" -> com.gameexpert.terrain.Blocks.OAK_WALL_SIGN;
            case "minecraft:spruce_wood" -> com.gameexpert.terrain.Blocks.STRIPPED_SPRUCE_WOOD;
            case "minecraft:stone" -> com.gameexpert.terrain.Blocks.STONE;
            case "minecraft:stone_button" -> com.gameexpert.terrain.Blocks.STONE_BUTTON;
            case "minecraft:stone_pressure_plate" -> com.gameexpert.terrain.Blocks.STONE_PRESSURE_PLATE;
            case "minecraft:stonecutter" -> com.gameexpert.terrain.Blocks.STONECUTTER;
            case "minecraft:straw_bed" -> com.gameexpert.terrain.Blocks.STRAW_BED;
            case "minecraft:stripped_acacia_log" -> com.gameexpert.terrain.Blocks.STRIPPED_ACACIA_LOG;
            case "minecraft:stripped_oak_log" -> com.gameexpert.terrain.Blocks.STRIPPED_OAK_LOG;
            case "minecraft:stripped_oak_wood" -> com.gameexpert.terrain.Blocks.STRIPPED_OAK_WOOD;
            case "minecraft:stripped_spruce_log" -> com.gameexpert.terrain.Blocks.STRIPPED_SPRUCE_LOG;
            case "minecraft:stripped_spruce_wood" -> com.gameexpert.terrain.Blocks.STRIPPED_SPRUCE_WOOD;
            case "minecraft:sweet_berry_bush" -> com.gameexpert.terrain.Blocks.SWEET_BERRY_BUSH;
            case "minecraft:tall_dry_grass" -> com.gameexpert.terrain.Blocks.TALL_DRY_GRASS;
            case "minecraft:tall_grass" -> com.gameexpert.terrain.Blocks.TALL_GRASS;
            case "minecraft:target" -> com.gameexpert.terrain.Blocks.HAY_BLOCK;
            case "minecraft:terracotta" -> com.gameexpert.terrain.Blocks.TERRACOTTA;
            case "minecraft:tnt" -> com.gameexpert.terrain.Blocks.TNT;
            case "minecraft:torch" -> com.gameexpert.terrain.Blocks.TORCH;
            case "minecraft:trapped_chest" -> com.gameexpert.terrain.Blocks.TRAPPED_CHEST;
            case "minecraft:trial_spawner" -> com.gameexpert.terrain.Blocks.TRIAL_SPAWNER;
            case "minecraft:tripwire" -> com.gameexpert.terrain.Blocks.TRIPWIRE;
            case "minecraft:tripwire_hook" -> com.gameexpert.terrain.Blocks.TRIPWIRE_HOOK;
            case "minecraft:tuff_bricks" -> com.gameexpert.terrain.Blocks.TUFF_BRICKS;
            case "minecraft:vault" -> com.gameexpert.terrain.Blocks.VAULT;
            case "minecraft:vine" -> com.gameexpert.terrain.Blocks.VINE;
            case "minecraft:water_cauldron" -> com.gameexpert.terrain.Blocks.WATER_CAULDRON;
            case "minecraft:waxed_chiseled_copper" -> com.gameexpert.terrain.Blocks.WAXED_CHISELED_COPPER;
            case "minecraft:waxed_copper_block" -> com.gameexpert.terrain.Blocks.WAXED_COPPER_BLOCK;
            case "minecraft:waxed_copper_bulb" -> com.gameexpert.terrain.Blocks.WAXED_COPPER_BULB;
            case "minecraft:waxed_copper_door" -> com.gameexpert.terrain.Blocks.WAXED_COPPER_DOOR;
            case "minecraft:waxed_cut_copper" -> com.gameexpert.terrain.Blocks.WAXED_CUT_COPPER;
            case "minecraft:waxed_exposed_copper_bulb" -> com.gameexpert.terrain.Blocks.WAXED_EXPOSED_COPPER_BULB;
            case "minecraft:waxed_oxidized_chiseled_copper" -> com.gameexpert.terrain.Blocks.WAXED_OXIDIZED_CHISELED_COPPER;
            case "minecraft:waxed_oxidized_copper" -> com.gameexpert.terrain.Blocks.WAXED_OXIDIZED_COPPER;
            case "minecraft:waxed_oxidized_copper_bulb" -> com.gameexpert.terrain.Blocks.WAXED_OXIDIZED_COPPER_BULB;
            case "minecraft:waxed_oxidized_copper_door" -> com.gameexpert.terrain.Blocks.WAXED_OXIDIZED_COPPER_DOOR;
            case "minecraft:waxed_oxidized_cut_copper" -> com.gameexpert.terrain.Blocks.WAXED_OXIDIZED_CUT_COPPER;
            case "minecraft:waxed_oxidized_cut_copper_stairs" -> com.gameexpert.terrain.Blocks.WAXED_OXIDIZED_CUT_COPPER_STAIRS;
            case "minecraft:waxed_weathered_copper_bulb" -> com.gameexpert.terrain.Blocks.WAXED_WEATHERED_COPPER_BULB;
            case "minecraft:white_bed" -> com.gameexpert.terrain.Blocks.WHITE_BED;
            case "minecraft:white_candle" -> com.gameexpert.terrain.Blocks.CANDLE;
            case "minecraft:white_carpet" -> com.gameexpert.terrain.Blocks.WHITE_CARPET;
            case "minecraft:white_concrete" -> com.gameexpert.terrain.Blocks.WHITE_CONCRETE;
            case "minecraft:white_glazed_terracotta" -> com.gameexpert.terrain.Blocks.WHITE_GLAZED_TERRACOTTA;
            case "minecraft:white_stained_glass" -> com.gameexpert.terrain.Blocks.WHITE_STAINED_GLASS;
            case "minecraft:white_terracotta" -> com.gameexpert.terrain.Blocks.WHITE_TERRACOTTA;
            case "minecraft:white_tulip" -> com.gameexpert.terrain.Blocks.WHITE_TULIP;
            case "minecraft:white_wall_banner" -> com.gameexpert.terrain.Blocks.WHITE_WALL_BANNER;
            case "minecraft:white_wool" -> com.gameexpert.terrain.Blocks.WHITE_WOOL;
            case "minecraft:white_wool_stairs" -> com.gameexpert.terrain.Blocks.WHITE_WOOL_STAIRS;
            case "minecraft:wildflowers" -> com.gameexpert.terrain.Blocks.WILDFLOWERS;
            case "minecraft:yellow_bed" -> com.gameexpert.terrain.Blocks.YELLOW_BED;
            case "minecraft:yellow_carpet" -> com.gameexpert.terrain.Blocks.YELLOW_CARPET;
            case "minecraft:yellow_glazed_terracotta" -> com.gameexpert.terrain.Blocks.YELLOW_GLAZED_TERRACOTTA;
            case "minecraft:yellow_terracotta" -> com.gameexpert.terrain.Blocks.YELLOW_TERRACOTTA;
            case "minecraft:yellow_wool" -> com.gameexpert.terrain.Blocks.YELLOW_WOOL;
            default -> -1;
        };
    }

    static List<String> states() {
        try (GZIPInputStream input = new GZIPInputStream(new ByteArrayInputStream(
                Base64.getDecoder().decode(GZIP_BASE64)))) {
            List<String> states = new String(input.readAllBytes(), StandardCharsets.US_ASCII)
                    .lines().toList();
            if (states.size() != AUTHENTICATED_STATE_COUNT) {
                throw new ExceptionInInitializerError("authenticated state row count drift");
            }
            return states;
        } catch (IOException failure) {
            throw new ExceptionInInitializerError(failure);
        }
    }
}
