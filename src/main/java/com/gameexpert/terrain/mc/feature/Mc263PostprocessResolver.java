package com.gameexpert.terrain.mc.feature;

import com.gameexpert.terrain.Blocks;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.zip.GZIPInputStream;

/**
 * Transactional executor for the pinned 26.3 {@code LevelChunk.postProcessGeneration} loop.
 *
 * <p>The authority supplies the version-bound fluid/block/updateShape implementations. This
 * coordinator owns the official section/occurrence/direction order, live rereads, tick identity,
 * center-chunk lifecycle, and fail-closed catalog boundary. Nothing is committed until every
 * occurrence has produced representable states.</p>
 */
public final class Mc263PostprocessResolver {
    public static final int UPDATE_FLAGS = 276;
    /** Append-only official POST transcript that authenticates the production closure. */
    public static final String POST_EVIDENCE_SHA256 =
            "70d3e811c086491e7ea1f5d09bc82e650eb56286bfd8e1c03c79add8249e2e9a";
    private static final String NEIGHBOR_TABLE_GZIP_SHA256 =
            "e9acfed515c98a990d5c925150e5f0fa5e1f5baa43a44bfdeba003c61d4c5b16";
    private static final int NEIGHBOR_TABLE_ROWS = 3_064;
    private static final String NEIGHBOR_TABLE_GZIP_BASE64 = "H4sIAAAAAAACA819WW/jWrPdc+e3+AMkarAdwC8XCJK85F4kAfIgHAhb4pbFa4okSKrd6l8fTrJIkdzcQw2Ecc6x+rRqrRpYrD3Wf/z7//m/+//13/7nf/8f//bv//vX//t1CSJ5TMUp/6/iKI6B2PtxnO5Oxa/R54cUWf5yFuHpI4y/ZfpyLv5QfoTylL/EiYw+TiLM5EtS/j/p15/++bUofpbVP8vq9/K3f/3yfi3+y39AYufpdRB6Xf2zbqDXBfQbDHQafJ7Z9G6Bjyn+Vv3z1mC/Fdhre+xrkrD5exibxN9taHJ/j4Aj+juK0/xs9YBTgI8pDmD0EWwdl+NpruFzr/rHa8C9AnzpAG74kMNqbvaUw/rc9DHH05zG51l8tXzOAbLrJDi94jo+x9Ncw+cA4d4CN3zOYTU3e85hfW76nONpTuPzb2lZrwOYfQobsYwZhtZxOJreGv4GCLYHtuEjDqq32RMO6m/TBxxNbzx/n2R0lLtylNCoVVUSze9Vtml+/xa5TMP48/MO+1JSvTO455XTT65ZFAwWFAxKg3QJLMEJVEbXt8AbKYG+Ad5B8CtMuxBYkxLoG2ADjW8YAEdK/L76vjV8heOeAjxSAn0DrKDxDf0vKPH76h8g4B0e/y0lfl/9V2B4Q+dLQvi+8icz9FCK3zLb+UGWi4LHx/KlqDqy4pOM8jG7//Pzil90AFcnBMRaQzTAYePCAnrkNvWobepR23RFbtMVtU3X5CquqVXckKu4oVZxS67illrFV3IVX9FVjD934k+QffypR9er+tV6agQVHytJSwNJNzBJf10lJaGIvjJHIZlIwiD63BUu+ZQfi39+bN3+0axCurKWbrJCcdjlt0R+HOI8jy/DsVgERzc2liWAZ4jgx9dDKMcQHGz7g5DHyZh4r2ser1Sg0EJHfC6CNOsvETcGy86iQA6iSKb7asZtzILbqtbf3i24/VWJqqX88t61bKlPpZ4EG+MiqmGXuHMRXS4bMC5FST5lll5gdagsgKmozULFJctToSRynxttiHhdIktzIq0lT9O43VRTRJs7l82vWtadjIAmo3ZR7ynqsnmHY2MTu10yC2gyhtGLxmYyfu/z6w2V9ROVN3MqrbU80wDuJbtalkO2U5NRe+m9mnJ+v7N5f2KzhWNjE8BdMgtoMoYBjMZmMoDvK2MNlbcnKmtzKo+1KtP47UVMJcohYJRU1C7qvQw6XAQYF5vY7VBZAFMxjFwkLpNxe9+40BBZdolobWL4jmMfaDj3EHWDE+UwoAtSu7FSGAbXi91XLzI/37J8fwjj45c18buUY3gthvE/699+/B2NhYI10ymM+3wBHERV3CKr0cOAV6OucZD16IPAK1K/65AV6YPAK3JNprVYQiIMqeAGUOVvZFf0MBw8EfkyC3Jpnen+ilCK5xmbpe6MTfVt0mU6K0SXKddhQI9cRY9axRW5iitqFdfkKq6pVdyQq7ihVnFLruKWWsVXchURF7H+XlNZ1OdXmVu98g7icojjXbWu81LTLqq0SL44rfW0pC7vUkORfiKLXcKJxbFBdilGYxBiOTfzAjGw38yrTwBpM6+aAMFm2j4B4t2cB5GmMuwMTx/75a2nOLpS68EiuNh66AYuthhLgcushjZAUoNixDTylludht46SoHSTx1mhA5S7qNCt+5CY5Fxb8Wb87cM77kRQHSzCIQiu5mfR5FdT6FCic7TOM6znctrpyNkCSHEgxCyshUShjuR5+J4vpQ12VEG5a6Ul3ZADpy8AYapgxMfpw5UfJwqaBFgTmEcp7q+0T2QoQSZ9MwKAmXSLyAoU17RNljwuffTIClq2VMnc+dBmNcFM9BU3xBS7RESqNotJFCVb1CR9sWg84KxVjEKArqSMIoCOs0/igI5gX0I0uOZdeAGQsBh3GaPXw/kiOCxRo0qfIpB4zM+9Zixwidc3rACdJiL08ADPS9jgweunkftP4/Yfx6x/zxa/62o/bci9t+K2H8rWv+tqf23Jvbfmth/a1r/baj9tyH234bYfxta/22p/bcl9t+W2H9bWv+9Uvvvldh/r8T+e0X2n/txzSdBNyhBfx0FuZ3VrGU0xysdJijmcGLQmAnegUEDKqgH44Z4sB1FMyGDe9xqiAnH8YNDKI5f+4P0u2vix+M1CX5mUBKRFik/jnPLCURtjLMUPhBGswqPq8g4CKAmzcI/ribjIICa1PsCcBUZxXDV4yjSpNmsZnEuv5bxGYq/0t8Xj3EqjsXzKdpPhP1bWC27ClIs4VXcYAkvfekou8ywUSG8wMgyN1HfcRw6SMjy/emaRuIoO2kwDHLXXTJDsuvMhCS8ThZIwqsHGEL2VaK/1zQhXFJPFwLnraaLAacHzjtNFwNOD5Q3miaEoxau77NCRHCUTo9nXNTAgW+vwuONYU0jjmR9StR5cP4k6QYmyWF4Hsdf2VmGJ2sBxcgj2h/jVIQ7mKNqLYlup3Pbgk4iQqD3LcJaNPxhVhUM5FlTFQ7kUVAVDuA5x0MqvwuRZVEX+buzyPblIDeU+0WTF1t/tOz/kedyFtUZ2/6Uqhl0NXXJo3UfGkXpZxhCV2tBEylN5WgdZBeVg3KYhncjngbAgz2AfNjr8KakP6jbC+efI3bk0fIfHo3S8nq28J6abDQkCgHl5OwSi0ErFEAJME2QuzJpBQUmEYOw6PY/8e40Kre8ubhFPzBgKfBc9OXMpBUZmEQMIqPbEsi706jcsnZxi35kwFJgWcpy5NEKCzwaBkHRbYfmNSQqh3gODtEPCUcC5UCw3rAdxt/Nfu36eHq1Xbv69Zood62Xf6ddVp6aTdvVBIFOWWbL4bFvH5BChdbjUBKbMEPxV7oU3mkoPKyAzSAv7xeYsEL5d3hItE5xoHCogPWfij6JDREJlSGsOVTAQBFxICKhMgQEB8OI6CepLREJVaIE5nB3hvqUV88dJyoWqpgwI2G/UJ3G3xH2qqc2hsMC1TMGyrqnPgigJigrn/oggJpgrH1qY7jq4bj6Wcm4XLNzGscXBxp3Ec16X3nta6OlxkHTRxZqnXB0yhyAdB7jCDY2j4Q8B9s8HwXmIFNxmE3YDLDhN80sgqZPhtYwvVPdrDEzwYbfNGwxoybDZhj+NKMmw26YOQQMR45R3txAHjAGbPhNQxoy+mTYDMOTY/TJsBtmDgHDnGP4yxh9MuyG4YoX7hpG0dF8JuHCnV24CxhdKkhGqQBmMx1jx4bfNPgRY0WGzTCECcaKDLth5hAwzDmGqX6xIsNuGK54YalfJqmwpxeW+kWbygyChSG3zGD6xYoMu2Eow4V97mWSCktuYZ950aYyg2DhzS3sZQv7rIs2FaZYYa5Z2KdcLKgwG4U/UFByCsz1LJUogMsAKjnV+eWDKK8i69/O83y+UW+Xz4DI1qU8YDJbd/GAyXxcwWMs8no4hBL28oCWSMfbA9qSAK8PaIvFvD9AhQN6gYAKCPQGARUQ5BUCV98vzznfuzLbB1B2tiJwFMf8OtLSamkgYX8Ky9Y4Vo/mUYRHh3bRR3FJTkE6co/VSxZ8RuVTVfwN1cW0VhsvnaCdjnkbII9fVguLXL+cOazdQ0ZW+ek2M1KlzbBBY0wFTWZx8ihTQCMr/XSvHanWZtigYaaCJrM4eZgpoJGV7t5wSKq0ETRokCmQqcxNHmLjyOAqR34od/V/srJvd5CrtTMvOjsIHjrCCh1hjYGQpo4Na9silu4iPHcRK3cRa3cRG3cRW3cRr9Yi8vgzFcn5ts9FMdC1H3pdQz+No1EW6i//lnsRpJYqFF/+XXxsovvlIMvm3JlLZ8lRmfbj+yeRSwSaSwSaGOZcYtgTxaAYFvUwiHoIRFcYRFcIRNcYRNcIRDcYRDcIRLcYRLcIRF8xiL4iEH3DIPqGQPQdg+g7PFGMNIqQRT2MF6iH8AL1MF6gHsIL1EPxPIbrMV6gHsIL1MN4gXoIL1AP4wXqIbxAMTyP4HgMvyO4HcPrCE7HKJoQaiaMkgmhYsIomBDqJYxyCbZaqrpg5jtQmk8yHWieCxG3/UlGR7nTOCI1crlao9TyZ37aYGYUhkFtgjaBJTgB1UV7AxZ4AyHQ25GpT2BtTUC51VyfgGdG4Lnx7hKvk7EdokOvXx1A0F7GI4AeuU09apuuyFVcUau4JldxTa3ihlzFDbWKW3IVt9QqvpKr+Iquonu/9mdJNzBJf10lubRb74rYFQ5xWKo+FzVFZ3th1eUmKz6GcKvy2iD2bYw6GE1TjRJE1Wr+p1WPI4ayYfpDDTcQRJeoUYB80rSz0PXJ0hFDyyeeIwiiT9QoQD6p+0noumTtBqHlkTc3DESHKEFc/BFkMpR+kc4jP8vjyH5ryo+kvGpg6nSR+Y+w/Ho6gQhxJRREkTiI1Pr7objZfzfOCi3kTR6K3zLLC6ePseuhqUpCnEqH76eZ3PtBmtuLKI8J+XtfyiQLhcMZlkqQW8A/RGD2L+zA6LTIC8rze3tVRl1ui39ti5+GxbbbJe+dhE9rdzEDHeXbYFkeIRXFT0NIdAltaAi1DETKp3jzT8VPL4o7fBYkfFS70/HpqONnFoS4DITaKhSGDVDDUB0yj3Zr03nZuyfCxf3jo2/nu/67yJZJq/UbKRHls+Tdc9/i/vFBZUNApWUUIibT+bff6vfBZIHPRNVzGJOIOk6YqdAbBa8NMQAPiGbE4zTGGgFrlL5FsfRrU/w0+X7z1AtYWCZ8Q0atFxALIXX50qvHu5TeiSipRgi4jGxK4C6jBQ0jVY1HQciwDOahxGck3BbpQHygGqVr0TEqh++pcXH/2OpWLtxfHSYFMTEVdanTq85bZN4pyKhGClhcbMriFpcFARdVDYhLxbA0pibDYZjJ8vie7Bb3jy0mb+hMWibBIFKv31lVyL3JxlqW62yjISPV7CcFIXVlUxScv96Ln4bS+xOlLRGllpGIGdlUyF1GCxpGquKPgpBhhcxDic9IkxXyW7Uq/3bn8/bEZ03Bp2UefDpGFXJvSrLZt+EyJ2nCRTU7iktFXfHcs+Hi/rFFZktBpmUYMi42FXKLy4KAi6oQxKViWCFTk+EwzGSFfE92i/vHFpM1OpOWSTCIVBuqrArkXqlViXKttMz4qCo/fDrqoqY3o90hJGgIqWbYMfnYFMYdPgsSPqqKD5+OYVHMQYjLQJMF8bLZndywWXbZeARsWqbBJmNUDPcKrXrPrEudZcBEVfFhElEXNr156wcVQUBFNYOOw8SmCH4wWeAzUVV6mEQMC2BaKvRGmSx+l61TINXHBw8Pm0fLHCA0yoYK9ZHvMP5uTnyXv9Xnvcvffhq7DJ/6Lv5K+3xBZ5u0/gvQmUZUSOnykPA8ShBje7zy8OgbZAtFpIJ38IxAIHI3ifKGgp5vVohMjIKEi0jfOZ4bkwp05PlV+qbP5IjHxMg3Pg8PGoNYReuGiUjfJGswJmZJrWeSdyYifZO8YTCxCpMlF5O+URZcVPJCBoFVShhzKnBBW+G7WMUxpVT483gDtqgYWsWtZPuWB8vTn8dYNLesK7saWgob6WdoKW2kk6GltOEehmbClpBWW4JabQlqtSWk1TxIq3mgVvNAreZBWO2SxFmRMnah/C3De/+QZfdg8rLMEt7UweQkkanrWe9KhtNp7zQa6VGoZY5UHL+gbhMo/112fXTrvZFK8VVKOUuR5s2VPi/dPy0nfHL54cfpRUT5SyTyaypCx569DsBOLXoNcK9JGse59ME0vpUI8SEL/EBE1mKuOcC9FjcR7Q/S7xyBio/HaxL8vNiTwhIfp8IClo++LkRhch8Got5iiqvGOAacHvVGAFw9xjHg9KhmdXHVGIVw1OIo0kTm1tdblCI+Q/G3vKflp4V3v/O27ZM7KrpVRYDLbtUU4LIfFYataPdO6b64FFWPvxfR7yBUdUnXu4RoUNxglecgb7DOc5A3VOnpiot8GQaKLl7qb6df+1h8cd7mDMbB/j5nEwpINzpDUegb4R2IAcGt0lAU+kbYwDMwDIQjLYO+CXwHAsSXe0NR6BthBc/AMA4ELYO+CQ4wBBzSwZaWQd8Er+AEDINAkhLoG+Bkik94z74tpsM1zaOQHoOaHr2aKwY1V/RqrhnUXNOruWFQc0Ov5pZBzS29mq8Mar4SqOl+JX5f1g1Q1l93WWXHJfuFhh8xc7ho04wM6q2Nk1SIrgQ04IF3u9oICbYLuwz54N5pNE2G5IaPERo8p9inyRCdGTchgni4dIQGx4GqSSo0B5gMaOCde6hIJGmQXUQa2K+3+lL4+8M1O9vNjstjnBaa+fskznfNJoKmHmpne6AWDRpwdc6hw6sfIzq8KjzA4Byv9f8RsHdtVtCW5LoV5iHLD8QljnwYYfIiUxECCfuMoSQFaRzBSApFEmQwolJZ7wJxkgbgvCBOg9z167PoA2HBBa/pghEZ7HGXORe8bgJGZHDHgINMuG5ytmGDOQYzooN9PbENG8SbgM3oII9PB7lw3bZowwbxYkMzOthXCNqwQbytz4wO8ozCIBeeG5EsuOBdP2REBvmqHwsuePfqGJHBnXJpmPCeRtZkgXzeVcnC+VyaAQ37jn7ld/eJyM+2G4j9IEtklMm02+G3eBQ+ZdqNOyt6T9KbKWQs8c0kKJb4a4Inu54nhJKeBklz9MflGBPAFImMysmvtHiarCYkH1/fn1JxkTt5k/35SOvukNPiW/uoUeS39lWjyH/ss4YSX2VBNOO3pSPYvi0ewfRt8U6WP4n0EorI313iIMuvqfw50AghawkoywOUtQKUtQaUtQGUtQWU9WovS6Z25ydOQVrO6Revhd1UC21TgW7vqpack4jgyZVFYiV5bB0MBaW//IUC01/1QoHpLXa5oZzCm/1SZ324ulyYs1prq79eHrMVf0UoxfPte8v647+mrt17lkO579gV22FX3CS0x6i2x6f2ilHtFZ/aa0a113xqbxjV3vCpvWVUe8un9iuj2nj7m0/XNBJH2SmLwiB3nMB4klqXQeBi67IHXGxV5sBJ/ZH0AnGsf1Sq24n+UbFuh/lHxTqd4/8MRZbtExE5n3e1mv1+hrc/6Gl1PepnkSz2YXA8y2jnx99RI1bjGPrUisDg3jW9chyPlP0YA5hTHU7zstODE5OZjJ6xGXCai5n4g0lBidZIFe7MUpMWp7mYiS2WdCjxGGlGaUmH0kyMxB5IrDmpdzvDDHKSMae5mIk0lEwp8RiJOSeZUpqJkdgDaS45aS5lkimlmRiJK47mUSMNEJpPOppHhaRDiDuIyHORcmqLKRk5cZqLmdAjyYUSj5EYEpILpZkYiT2Q5pKTOOsjF0ozMRJXHPHVRxOE5pOO+OojU0LcQcSZi+YyfeRCaSZGogyjecwdTRDizUXzmDkyJcQdRDPJRTMpi+Yxa2RKiCmGZlETzWPKyJ7QLAzEHEA0OagSOrPtR1Cc5mImnEACosRjJKpsBERpJkZiD6S55CTywgiI0kyMxBVHxLWRPqH5pCPi6siBEHcQceYi1vkiIEozMRJlGDHOF+kT4s1FjPNFDoS4g2gmuWgmZRHjfJEDIaYYmkVNxDhfBEJoFgZiDiDqHDSb7UVAlGZiJOwomsneIn1C9IloJjuLHAhxB9FMchFjPTSTXUUOhJhiiK0emsmWIhBCszAQcwAx5qCZTA/NZDeRAyHCEJrF3NBMthKBEJqFgZgDaB45aB5l0CxmheaxiQiCD4N55pJ+5lABzWFCiGP/UNlrzemuWNdubZ+piBz6kBVfz7Jag10Wxd83x4vC+vIexrUT91uGDt++7Q/S71wIFx+P1yT4eToSkRbxGMe5ZQDoQpyl8GEg6gvocNUYx4DTo77xDlePcQw4Paor9nDVGIVw1OIo0kTmthd3VyKKfJqKY5znwukp/Y5jh8dcygj9OdfFcHHJEwbOk64NAqgJzrOuDQKoCcrTrovhqofr817KcHtSg6ju3lqqKj9OYRyn/atEzZVTiG3dJQoqt3WZKKjcx22ipmLPIvos791Ni0jMdpONnF2F2lfN5/LtVpWI4k+QffyxLg+fBN2gBP21F1S1mN7JSBzC+xijcWw5Whlvpq0WmkagjS4eAt0GLy05cI0uWkIRG10oUCAbXShgIBtdKGAAG10ER+txZhCdCh7S3x/PQSaLR2Pf9ARLg+NX5izVud38j6RLnGU3WHKVMBgprpTKhu4HkWY7jfMN+rdNL3Sve4bA7982vQSGN7xr+40Qvq/8OwB6b6peX/k1IXxf+Q0sOtYl6+7ofdV9J/A+TItDixm06jbXyw/Ae4TwDrfb66EbRp2gQ++rfnAHd0g2Wzr0vuqvyODjyxHQ2IYBJ8nA+4qfcLFVNneCHsuv/Xc+SI45nkUQNWPgl8mBv15XuUqwH8fdbsxVi/Cqw8/LufhD+VE1TY8TeT/Jn5T/76lJeNMj/I7+r18eBHLdIp0M+loO7FmUbiOTKF0PgG1c/Vb989ZAvxXQaxBoHbUhsQ2djQVNonY9D2Hj7XX1z7qBXhfQbyDQOmpDYht6GwuaRO1qOsjG2V71j9cge2U7UQhkHaUBoQ1djYSMprTDRpL/vEafoeR541tgV2UWwAOiDY3x5rUBH1Pc9D2gxEYudiywSfyNXWnZgCP6m67OswEfUxzA6IRVphX6mOqm7wQ1OHKRawNO43PsEtsKHdHndBW+DTi94hiFthU6YrjTDW9swGl8jj24skJH9DnZ2M4CG7GMoRtY2oAjFq5kw1oLbBJ/Y4+pbcCB/X2S0VG6b9dYtjZr6K4AADGoFzeWnd0i0AQ0lrTaBN5ACBiuZLYJrK0JOKygtwl4ZgRCKX7LbOcHWS4KKh/LlyL2s+KTjEa3FPzzE2iPbcaLeh4KHvG+goYEOBxflIDwCnrkPvSofehR+9Aj9uGK3Icrah+uqH24IvbhmtyHa2ofrql9uCb24YbchxtqH26ofbgh9uGW3Idbah9uqX24JfbhK7kPX6l9+Ertw1dsH8afrufAniXdwCT9dZWUiSQMos9dYczPosa3O3jzJcNkV35/4fj9pbMAVw2WnquAlauAtauAjauArauAV1cBb64C3l0FOH7fc41kz5mBayR7rpHsuUay5xrJriZwtYCrAVz1d32QXZ9j18fY4SneJ6GIcquvh8L3ZTp6qNdhUWxC8mOfvZvg/glhhxWOKdEP0m6S++eNHVYnpkQ/SLtJ7p1edljNmZD8oGwpuD/73rvZwjLsRiW3LrewDI5R0a37LSxdOCr6ccWFuaWjQma0a26lUA3xzE+8PAsfH1zZpLwkyFy254Yi/ZR7cZH5+Zbl+8P157aY8l4JOJraKPbXCIyDqF4FiCgYqihfEZgwGMooXx2YMBjKXBO4hKGJMaSGK4TqzQhnLMDbQ2qQU51h7/tlYERVWzFsRf0W1l/chfK3DG3Hsi0JnrOElbMEy7FEKMVpHwZ5/lR3Z/LzIqO8iKr4GuXWNtKT7qFKX6FKX4NJr18GaIYfFO/hil/hioezff3uQrP9oHgPV/wKVzyc7auXFJrph6R7qNJXqNKt7X6sRkfdc0vZ/hDHX4oTUubXTD4B3U9O4CPd927jIzWbSFGAftfelx/lRXDtGYghBPMgGBRf+whPfu0ZPPmVPyDEl9uA94fwKpGvIzYEsr83dhgI42JiUyRonTCuKDZFgtYJ4bJiQyAQjT5D8Vf6ravGd72JVZsZNQ2A1vwqEkJrmhUJ4THb6gbgftN7S5jLLdK1GILmDoZArrFO0ejBFAlaJ7wsS9H6YRgJLcsStIFoAbldDt8ShJmu1QAQ6VqNAJGu1QgA6boCyHJR/LFfAmUZgDyo9O/c7iMMQnF0n0J+SHGZPQ7C2z4+7fOz3P8uBhTyZi8mEb7dEkFwwR9uXPAHGheCIcaFYHBxIRhWXAgGFBf8ocQFfxBxASlc4/gC8xJrCXJ8WbUkOb6UWpKcXj4X8SmjXCDnIhMU+8AZQMHISEYwoNpg5CUjGFBtELKTCYqTLhfh1oTlIqLPNP5NekjbFtPhtIgeJOiBmFFIj8GyHr1lPXrLrhgsu6K37IresmsGy67pLbumt+yGwbIbestuGdTc0qv5yqDmK7OaSM+J+8HOvqwboKy/7rKSNE7E5zWU9fHMl+7G9PrQ5wJqm+Ykan0j0VxA7beKjmAuORRdMijqcSjqMSi64lB0xaDomkPRNYmiynazLrlV1XLWRe53HPtgL6aHsBukMIdXk/SD6wX9pJAJjEOQjaNAnhUygUFRBvS0kBEOijqg54WMcFDUgTsxpA9if2RIgQF5ZsgExsUrYRxVqq9OQwOR5dR398UI5+Jyd8qTlCWIFA9EygpEyhpEygZEyhZEiuWtCmXrZ8ep+FKC2z6auv/0MT4cQunUOLonqNoRkmb9xiOHOM/jy0t2Fon8CKJIpvvqHuexOxm2xb+2xU+j1PZXJaqW8st7N1PPiVXrPgc2UvXVz2O2KpecRfHT0BJdWhtKWi1jMbAq3tRTcdV7WDqsFoSsWqZiI6WOqxnR4jVWlqdCaan7FRMNJ6/LaUnGqWUmGkp5nGjndO+ePhf3j5WcQsSv5TvQa2aST+sCFgY6yufNu2fMxf3jg9CGjFDLQKR8pnO31620vEWLz4KKT8s8DHTU8TMLQlwGmsrR3tM1QF6LzZKGTcs0aGRaPcBMS+6iJPu1KX6aN8bmVy3r/soQTq8MQ16tFxkjLXV51BsNdIm9kxJTjVIoeNmU3l1eC0peqnqSjpZh+c1JjNtgkyX4/f64htX6idUbHauWqahIGZXh94S6uH+sBVUvHwH18jEpxFkIqUup3tigRemdjpJqtILLyKYcbzFakDFS1ZsUhAxLch5KfEaaLMu793566zafNyI+LfPg0Wl17TStzHtTqbUsmLlUQ16qGV46WurKqShxf70XPw2x9ydiW1JiLYOx8LKpzLu8FpS8VIUmHS3DypyTGLfBJivz+6XRDau3J1ZrOlYtU1GRMqrMexOutSCAGVcTRqoZYApC6orqnkMX948tSls6Si0jETOyqcxbjBZkjFRFJwUhw8qchxKfkSYr8+5l/95bm8+aiE/LPHh0Hu22TQvzXkFXiYKp58xYqapMKlLqoqk3j9+hJShpqVYX8FnZFOQdVgtCVqrqkoqUYTHOR4vXWJOF+L0xTMNp2eXkkXFqmYmGklER3ivnKjkA1ZwBH1V1iU9HXTj1ZusfhAQZIdXqASYfm+L7wWdBxUdVVeLTMSy8OQhxGWiy6O427/KWLTYeDZuWaQDJlDdh78pNLuV9ei/VpPpHVPyPl6qKr/7wmoycEX8pYau/3d5zf/olHvvUTV+oE3SqXws+I0fza0LFN7t8Vuh8zOzjQfCpoGtCJbWOu5Tm6fM5ovGxs88amI9ZPPfC552VTt88b/R86j+gto5O9PT4LM341EwOaXD82mehOOzyW5GAm1pZe2ijebJHG0w1YHHC8uNrYf4XwAPbCrDyhaZdbfxL77XeQZrJGQEYTqBnBHQo0e3RHWfDua0MiBXstjItUoTbOMb5cK47arEinFIf58M3J6TDiW4A1GYzh6oIgs+jKiKlQ10WQfDpG2jBQEjlMGg+edliinAUBEJIZSAQPhUJnmF0i5B1S4TL1Xf4KsEwQw/EbXgxgAFc6WsgqCYKTQDmcC0CCBuo6xDMyGCf7AdhA3VI3YYM4kl+GDo8xsEdlQNwgRqNT1KhOooOQATkCLoBD8RpCVcWIEeGR0iwHRWG4QN2RNiQDvpJVxg+YAc2rehgnmwFIsRlIOQpRwg2YFON02TIjmVCUIE5jmnCBHPe1ZkHzLG5ERpsx+Vg+IAdkzOkg37aC4YP2KElKzqYp7uACHEZCHlVBYIN2CmuaTJkR5MgqMAcSTJhgrne5cwD5ujICA2mIyMgbKCOipiRwT71AMIGagO/DRnEUw4wdHiMg7uCDcAF6jTDJBWqbfoAREC25xvwQNxV4MoCZDv1DwnmfcLOPFQLv040OLbhOtMA2e6qzaJaE0fc7wJKpPw7fBaB2XEDyQPPHoYbSPpENoREVAZx4tHaMOK8o2ZLSEQVqQg87pGqPPnRd82JkokqRsyJuOzh8W/7p0Zjrh3BRmXeEGQ69Ae7ZudC0KVul1L26mr8VDm0/rX2aNPDsnJp/fvDp/L+d2fAw60rnBuNR0CzWqP1XNEbowLnD40BGozG4A2MPgsiU9SNJtlTxgQNRmPQx4WaBb0pGNOFmgWfKViDgjRXVCD85YUBDUZj0ISFPgt6UxDnCn0WfKZgDQquXMFYVuiz4DMFeUyw1RTDHPjSBFtFoebAGRD4OaKSzD9dYUeD0RiIUWHFgt4UFInCigWfKViDgitXUNcTViz4TEEeE7T1xCQHvjRBW09oc+AMCMocwTk9YcWCzxQkIcE3NzHJgTZH8M1MaHPgDAimHMFXRvDNSmhzoI4HrhqCb0rCggOXGRiDATo33I4yDK6XXRbF3zdXdbrCnIhFMj/LNBXHL1sJsfjaH655HkflVlr5cQrjOH1pn4tP4m+ZdvfvPv9M7d5RgtRHE7FR6iMm2CjVDmIEkHL3FbZT2hhoPmmDoLmkDQLpEb/wdP/KiLAU/nIu/lB+VCdY4kTep0wHgLu7zpflxUugwFXKG8DtHgleF7hvALj1WQMOjVvIYyp3z5m9FcBrS+BrkvD4eBgY38dtXFofjyBj+bh1vN7wQUZHHlPZ1dYjwDpuRtJZw89P1+2Ul73ZIhs+zIA6mz3NgH42fZyRdCbwc+tAsuHz7Jo8J5GJVdbxM5LOGn52De4WsuHzDKiz2fMM6GfT5xlJZwI/Pw52Gj7OrtaeAsYqSIZxdZyMo7GGj12j6wFs+CjDaWz2JMP52PRBxtEYyccnGR3lTmOHyMjBtQZ+2bqse9F80hmuu8LXM3PLzlXhoOiqY4wDur/RofdVf3cH703W66u+pkPvq74BBTd0+pEMvK+4b4etXMDV19yjQ++rvgIFN/S5IAPvK35wxnZ4zLdk4H3FXyGxDR0uqbD7ap9MofefhejOtF0QVafDGxx1/VImtPoHA3aseLmjmkz2T6BWUOS6PlBxVa3nb8wcu2p+lhi44+rWoJDqarkWXlsd30IoW4/l6X07ikupLplvx2Bxla2Gd/T5eAwWN0l1UMny8QgqpKqhFL9ltvODLBcF/MfypRiqZ8UnGeVjZc0/P0PjReetXm4JgYW7b1jBQBuuXcjQgFXzaP3mkfrNI/WbR+m3Fa3fVqR+W5H6bUXptzWt39akfluT+m1N6bcNrd82pH7bkPptQ+m3La3ftqR+25L6bUvpt1dav72S+u2V1G+vqH6LP11vKOyIucGI+eskJglFZH+LYyUhlVl2TWUpqhiXTWzuXWoOujKRhMX4znpzMGJ716542KarlewZdEM15KG+nb/X4qdDZANDBLkHqiEPvIaj2kRQm3uqWFC0juzjc/VrNGWijozeM9ul8g5EBbtLoykTxIaI+lRwWw8qeTweGQ179F8rrXZyC/vHhqSvXp8BVys7UybqGO31CupS2QJRwW5gZ8oEsVecPhXcrmxKHs8Prsoz/ee21WhrYf/UOCYPaBKIbc/6DHg6jRnyUD8kveqjQ0TAEEHuL2bIA6+ZlzYR1MZZKhaOT+ujKdLC+lGh6AxV4hfCk+FThY0rxhaIgQfrejz6S8ajNCwOUYxzKL1hagjLmQUNErRW6JfiTCExQkTfGjZnEBQkGINiiAWtHfp1HlNUjBDRt4bNMQ0FCcaoGGJBa4fei5wpKIZ56NvC5szQOAfGkBggQWSFH9D+vuouePs997ASjBVMSdR5lZtF/Rxzs6hCB4hEufNvnwWf0a4P3vK//VKUBkDlW1SEym+oCKVPLAEOWeAHIrJeRUxF9Cn3B+l3CtP4eLwmwSOQRJp/nOI4t7wxSB/kLIUPBdI89ciqjKNA6tLkDmRdxlEgdakzELIqoyDOmhxFmsjcuqyphXyG4q/093l1ZV1Ry4hdL1/aP82jwlu5EkF6K08iSH/kSHvhSZyEIiXdVe4C7LJpRonrcSnsMSm84lJ4xaTwmkvhNZPCGy6FN0wKb7kU3jIp/Mql8Cu2wo+XneP7Lb+GQWJX2/yRN7n3RZDdLL9fDEPKF/cxLm9T2R/Pxeu6U+1XO/ay4mM4OjHRHfpggj6uGgbFbLZiEGuqRkVStVk4JlZVjYqkar3uRqypEhRO0c84lOV14iK/yl3nz5I4kx/pNYoK+O48GpL6E1SyIM9bVKrHexZMZmSUKmiYmBTvSr9FpX5S2bikFCYJRVTIjXbn4uVbYiln3aHBxg90uL+9+VfMJwgxLNdOMGJYKpxgRL9Q9UPomjekbOvZRBy/CkHBUTpKuFx9ewmh3DdP2/4SZ9kuDxKXp2xY4NC73EBeKWd/CGP7vhcPMfXM5q5eW223Og3j7+Z2pfK3+nKl8rfqJqXiF1f+dsBRHEke5FyEISlyqSqPtR/I5OZ+QJPbuwTksfcDmdzeD2gie1cOZskmQ8hE9h6Cprc3dUIZhKa3OHlKaWFT55RBaHqTk2eVCpAlqwwhExl8CJre3tRZZRCa3uLkWaWFTZ1VBqHpTU6UVSrPsox81MioBldD09ubJqtMQNNbnCirDGLTZJUJaHqT02cV6iHQBDSVyckHQcPY5JmFfhg0Bc5gdfLkQj8SmgKnsjr1WGgCmsrm5KOhYWzy9EI/HpoCZ7A6eXqhHxJNgaNavYJhGROpkVEtroamtzdNapmAprc4UWIZxKbJKxPQ9CanzyrUY6IJaCqTk4+JhrHJMwv9mGgKnMHq5MmFfkw0BU5ldeox0QQ0lc3Jx0TD2OTphX5MNAXOYHXy9EI/JpoCh7d6tQPXYH+cxWlhR+iHwemxH/Ymwx7ILQzYDEYfyi1k4AO5hQGbwepDuQUZXDk0YsAms7p6cEQHTp9iJoZHHOgchqfPMhMjJA50MsMrx0gM2GRmV4+S6MDp88zEOIkDncPw9HlmYqjEgY5seM1ddQzYyGbX3VlHB06VZ7R313GgcxieKs9o77HjQKczPP3ASXunHQc6i+EZcg3H4El/xx0hPEO64Rg/6e+7w4anH0Fp773jQGcxPEPC4RhF6e/BI4RnSDgcAyn9nXgo8Jp78Riwke2uux+PDpwq2WjvyeNA5zA8VabR3pnHgU5nePqRlPb+PA50FsMz5BqOkZT+Pj1CeIZ0wzGS0t+thw1PP5LS3rHHgc5ieIaEwzGS0t+5RwjPkHA4RlL6+/ds4ctmPCcZHeWuAqz3DtaI9e815Mi1iDWL5vq/5Q8D/Qs9wTjUNwa2KSwRKAzfLzpqhTcgChWsnSPWDhQqWPdY8Cgo1J6BYkDYDMUW0+GG/VFIj0FNj17NFYOaK3o11wxqrunV3DCouaFXc8ug5pZezVcGNV8J1Iw/d+JPkH38sW7X1Zd1A5T1116WjKNb3XEzLC/RtjxP8pByLS/JtpQSRF/IfRu1Iex76j1DYPRs1MeA0wOjX6M+BpweCL0atSEctShGmYWsTuyeqqd2Ly7xtUh/S0zhHqbwFabwNZjw+kHDMvqQdA9V+gpVOpzd68SAZfch6R6q9BWqdDi7V5kMy+wDwj1M4StM4S42t29yl8T+3zjcZVH8fWs38rCpBNuiHi08rCQFUS79vZ8GSZbHkdzl5+D4Fcks+ziI4l34W6Z5cBTh3g9SecyDOPrw4+9oqvfPRnciCAK/38QEE/6acCr/hI6j+im9Zvn1Yuv8BTEF+4Z0lgymQ4CWAI4BLoHvh5IzCAwYUJuAJgS08XHUz4PE3v0LMniHJlbm6DSO1wNH8/r+ItNP1kffjASDIcjiQJuCixHCIDsXFETkyyzI7fve3QX5QZxCyPlMRQQhJ7+eTiBC9lkoDruqNW7Z2BCuf2HT/ptziwAMA4cNAtoEsLYHKAlQbA7oEaDeGmBPAGZjQI0PsYrzJOkGJumvq6RMJGEQfVpWLJWI7zj2gSz0EHWDE+Vko+RmaZpc5LHMduJTfiz+cZexBJDhAchYAchYA8jYAMjYAsh4tZYho3yfXcPTNd11PlXdvOXHMY7yILrG18whfKdA/Di9iCjHREhviNJlek3Ktu+IEN/SyTxlMX0Ux/yaWVbDlQRfCn9/uGZnFyFp1QU6O6dxfHGVMzT1rCvk25eFcaP42+7JSYPsItLis7VXfiTsD2kxuMmsBV0vyVcQVaqvTkPbRDS+XYSZvDi9Jp7lLIHkeEByVkBy1kByNkBytkBybF8h1zQJJfbGF30Qh00KPRCUzS8GKJC6oGyAMUCB1AVjE4w+iLMmn6H4W768ZJqKY/EyE+2gtn/LTwivwgxNeuV4NOmlL6yFpyIId9lZJPJDZEcZ+YXEfZU/gCbsBwHqpxoToX7WMBGqRwAeoLQ9luzK7FjerYVj2b2WjmSXijSWXWrhsMy/98e43Iy7P4Tx8cv+2f/eB2kcOUopklL5Zvh5x7yMVyGW+o4hDBUHwBBD72xgiIF3KQBC+SLG9UQbAckTbQgkT7QhQD1xFJEfyl39n+xj9RIG+chChN0q3ADIGgmkPARre5q1khBHx1Tar8uVMnAKQ6Vkt6pQKdqtJFSKdqsHxyeg9EP/LqB+sezK5fd28xP1MuE1GVibAtAGgIzT1kkILr01s1lw4TJLxWAm4TLAhdssMwiWPhVKo/QW4BljZYILt1mYYkVNhcko3GlFTYXZKPyBQp9TlFtaiAPFgAu3WQhDRZ8Kk1E4coo+FWaj8AcKa07hLlP0qTAbhSdOeGuUYSLM6YS3QlETYQ8SolzyuIuOfzrFjgu3WbAjxYoKk1HIEooVFWaj8AcKa05hqU+sqDAbhSdOGOqTSSLM6YShPtEmwh4k5LmEffrEigqzUejChHnuZJIIQy5hnjnRJsIeJJy5hLksYZ410SbCEiOsNQnzlIkFEVaDcAcIQg65n8CluyzcHtXhElsFqMeiqseh6opF1RWHqmsWVdccqm5YVN1wqLplUXXLoeori6qvqKpmIvJdXlbl96trfJyEnNPrwXq/5mMPqQsH+6sui29XFtjHqYsVahl5nB7Pu/s+6P7+7aUJoe8gbe41eW7WnJQ3hH4sFE2frK2gC5oFfgNa/oYBWiGMaUoI+tAUy7wDoOV1WZiQhXwSLSuxGpEL6k9d0GuCAamMWyzjKuMWy7i9IELSs8BBDqFElu/knS9DcSsGTe2jS+Vg8nHMuMTuHmqxOpUyjFcfZCIErI81EQJWh5xQ8LLA9ShhHFc3qQRpbi8ik9UtLM6NT54kOTQ/cakQnavDHwGtmwjrdoZjR8QsfD8E4sfXQzh6Dg1IE9g7FVsIuQjSbt+JKgoay9Une4MokuVo4zR6sne5Lf61LX4aU25/VaJqKb+8d12jOrB5FMH0ZNLg8zxum3KwIYqfho7o0tlQ0GkZh5BNkfGn4qb3CHbYLAjYtExDTkYdNzOgw2OcLE+F0jJedXOmd+fidbks0bm0zIJLpUz7ujnYu6e9xf1jJacQ8Wv57vg6mOTxsAglDeXz490z3eL+8UFkg06kZRASHtO5tl8nPHgssHm0zEFIQx0frESoDTKVU717IlvcPz5YLHFZtEwBTqIe5lqVtJuqaczmntk3v2pZ99QurFK7IZ/Wi4aBjro86VXZXULvJIRUVT8mH5vStstnQcFHVb/h0zEsbzkIcRlossRdV7fjr+9s1k9s3vDZtEyDTcaozL0nwsX9Yy2oekkI15eESaFLSkRdyvRq7haVd3wqquofh4lNudtiskBnoqrvMIkYlry0VOiNMln23lPb4v6xxeMNmUfLHPA06vUWq8q3N0VYy3KbIzTko5qxxKejrlyKUvLXe/HTEHp/IrQlIdQyECkfm8q3y2dBwUdV2OHTMax8OQhxGWiy8n2rGlO93dm8PbFZ47NpmQabjFHl25tIrAU5zCSaMFHNaGISUVc099y3uH9sUdniU2kZhYiJTeXbYrJAZ6Iq8jCJGFa+tFTojTJZ+d5T2+L+scVjjcyjZQ54GtXGH6vCt1dIVaLc6igzNqqqDpuMumjpzT936AgKOqrZcDw2NgVvh82CgI2qmsMmY1js0tPhMc5kobtsWsA2XJZdLh46l5ZZcKkYFbm9MqqS41BFGfBQVXN4NNSFS2+W+UFEoBNRzXZj8LApbh88Ftg8VFUcHg3DwpaSCLVBJovaZautdvXxwcLDZdEyBQCJbxGGw0ejWkcRfg6TD7e7fj4XcWoOhVV7kCdedcdraL27vfry/ihyEd6yfHcI4/jiuiW6EpnJKIvLzqjNoYzWH+6Ts8jKPCaOefBbQm75doFWVR66yOc0kF8yLXuf7LPr5RLf72as/08ZpIoTpuTISCrXFykwaDwJDKDwbxlE1g07lL3urY5xYHEaPqdKT2nwJpC5UOIxkuo9wmQkFaWZGIk9kBSMSE30dNfPHOJIi9JMjMQVRzqMWEw0n3Skw2geJuIOIs5cpLiZjiuIjCnNxEiUYWTKiMVEvLnIlNE8TMQdRDPJRTMpi0wZzcNETDE0i5po9NrIGYXQXLLQLAoiMz4o5tG885sygpwozcRI2FHkwojFRPSJyIXRPEzEHUQzyUWM9ZALo3mYiCmG2Oohrav4ZxRCc8lCbPWQPR9s88xkesiF0TxMRBhCs5gb0mr1MKMQ4sxBs5gYsudDaJ55lEGzmBUy5cMTP3OogWYxJWTPZw7m4Q0ektxj3qEWPXygKM3ESChBBMSIxUREWQiI0TxMxB1EM8lF1IUQEKN5mIgphmhrIdOW0TMKoblkIdpyCIQPtnk454OAGM3DRIQhxDcfZNqOfEYhxJmD+OaDQPgQmmceZRDffJADH574mUMNxDcfBMJnDubhDR7i3DOX7UFAjOZhIuQImsfeIH0+5AloHjuDHPgwB9A8chBf/TOPXUEOfHjih6v+mceWIBA+czAPb/Dw5Z55TP/MYzeQAx+68JnD3M88tgKB8JmDeXiDZxa5ZxZlzxxmfWaxCQiCDr1xZpJ2ZlDxzGDCh2P/jxT7UESF0Mj6bq1CRBIcv0K5q/+TfSzHblZ6/Cy16SllD2luLdpDpO3h0V4h0l7h0V4j0l7D0f5MRZbZPVtnGZ72l2t2TuP4shOf8mPx0mpu+A+41Cp3wIut8hC82DJ1gUldolh2iWPZJY5llxCWLRTd++lt7xL3pQyH719EGO7FRebnW5bvD1f/fplm+b6czhrgKA6v11GQqjEpuio9FAxV6lZT6Lr0YTCUqbsHoCvTh8FQ5pqAvWN1MRzetaMQ1fW36C7pobh6xE+DJJTi1G9oHJbXwMJqNA52TRJssFbLOgLVWmgEurWakhDo1kIj0O1xOTWBag8wYM2C/FzI3+fiEErrQewlLu/tbT88YZC73jrdEVqHLbTUOmCgpVa+AhEa5+f9QWQizB2F/NxtDiVnn4XisMtvibz3ugC8Dnocy4+vRZyOYcHqVV45r33v/78mL9rvAXVuun+8cUzbJvU6r1eiNBuvO5BSN57ptQ3tsNogsLLpFNQhtUAjZdijB53VZHMcrwzq4qeh5HUpLV0ojXVB14j0XtupbiN0gUdL7cDeE9jl9Y7Byybau7QWeLQM452A12TE31tAN6TWT6TeXEixNYN2oYXZhNmOF3rvYxdamD2H7Xght/pVk2LqAulACrH/ohUr7MaHDqQQWw5ascLt9ddQghg8UQ2cqAZN0ziPWUA4GOBxWRR/70Jxk2n2sXyy/OL+cdryLSmevf9aUlYgUtYgUjYgUrYgUl5BpLzZB2UhZX8I46N9h7ZEfBdvHPuvx6ksGWRZkb2sJv2S9HqUez+O07HZ9XJOUH5Ub584kfd7dqoGbE9ZqtNtb1l6ARq72qkzAH0v2RfNp3/9eoOBrt9vTHq3wMcU7zbtfiuw1/bY9XQyj7+HsUn83YYm9/cIOKK/e2tM2g6nAB9THMDoI9g6LsfTXMPn9xm4ppAp6xgHcMOHHFZzs6cc1uemjzme5jQ+7623avscILtOgtMrruNzPM01fA4Q7r1Vbyafmz3nsD43fc7xNKfx+fPeA22XA5h9ChuxjBmG1nE4mt4a/gYItuftHzz+NnvCQf1t+oCj6Y3n75OMjnKncYWk+sTRstVwXnv3IAyDerZv2el4j0+gd66I2gLPR4lwDaBx6KxN4I2UQN8A7/j4ygCA0b93jFXfAWtSAn0HbPDxlQ4A19/wAThS4vfN71vDK6+P0dffIyXQN8AKH18Zf+D6G8afoMTvm/8AAe+Q/raU+H31X4HhDZ0vCeH7yp/M0EMpfsts5wdZLgoeH8uXouzNik8yysfs/s9PhbXoAJYLW+CI99VkOECPXEWPWsUVuYorahXX5CquqVXckKu4oVZxS67illrFV3IVX9FVjD934k+Qffyx31LxJOkGJumvq6QkFNFX5igkE0kYRJ8uezUwN409I8BvF3tGAN7B1RLfL43ugM7i53Bcx5gK4iEdAy7YR3OMqSAeyDHggnsMZ4gI2+EbczKYR25M2KAftDEng3m8xoQN8qGaISpsR2nMyWAeoDFhg35sxpwM5mEZEzbIR2SGqDAdjDGmgngcxoAL9iEYYyqIR18MuOAeeOkQqcAH6/VnLr0bxLynNfimfCmElYyWeBRa14153bV47/4Kqki8YZJoXU7mdXdCePc0UpFYY5J4XGXmdTcdeU1IVBQ8HQqF5GR4N3MTmmP7MeCHqnpU+rszRpnY7ccap1G6yNQc9iNTDR7ktuiXjHzhMcJF3yaWG5kUPHgDZIgIuTX6NRlfhIxw0beJ5Y4vBQ/eCBkiQm6NXvHDFyDDVPQtYrkbcZwGb3gM8EC0RR4cv277JCjPAe/kn1xG/g9Gv+KzmZ/WQLgmqOIfhZoVgMvZ9Op09SEtOKKuMgzCwC81DMIArzdoYTxOvrtCOMywP0i4TWrb0ekPlrn44M60g7BpmQabTBmqenZxGMK782g9QQg02Ob7LRlhTvobU0Ke+Yfh03qi8OkYPFMOU1IATFpPFQYRtlUIS0aYSxHGlJDXI2D4tB4rfDoGj5XDJCsAk9ZjhUGEadnIlA/2eg0QH1UZik8HcQ0JihCXgXAXtkDYtEyDTcYg+dmv7rjzaKU+EBrfIgzrMxBR8YfNGYjq1/oMRBh/T3W7K/92+0L8U3MMsJpC0I/ZKSbVr8ZUFmBU8uIPnais3ahU+EBW8SyoZI7TRNe8nJEr4l1+lEq1l7CHznUb791VotTFNjpMXXygw1QPOhhKksosu6bV/utc7ibkLl3lDveq0ReLPm+qBFC9rI3l48zIHouo6TYIcSPcldea/YcR2Nq7ASPwMb1vJS8V3/uD7PYli4/HaxL8ZNVEpPnHKY4pYM5S+HAwdR7EV2ccB1afOuHi6zOOA6tPldnx1RmFcdUmSBLp78WxgBEgp5XGRN7gRf51F3kI0uMZVO8niTdwiQBaH88yTW+gaj+LvMGLBFDcF+nXPhZfoKr3hd4whAKo/5/X6DOUoMo/i7zBiwRQ/CKizzT+Dat6X+gNQyiA+tARDx3s0HFeyvuOYx9U4YfAG7RAAJUTUTwy0H7uC71hCIVQPy7GrCms8k8ib/Ai4RQHjfaezBuCTADdAc/Yj4m8wYuEUxzU6T2ZNwSZDrpfP4vwOYpIVu3oF5bzZ9fwdLXvLVF9u9Aq+JK7/BwcvyKZZR8HUQzGfss0D44i3PtBKo95EEdaHeW1r59zhO7P4SEhT7cFpwEGV/iUXrP8erF19IIO3aFNujE4YBd4V2xwtS+B74eSyeEG4ISKo7tbGxpc6TxI7F29oEAeXoeBB0Z3sh4uhof3F5l+cj3SZvi06lP4XBvdRfXoVF0/v3vcRG/7yHQkVXec20oqbHEM4mu2/0zFbxnu/OKVVqi5cCiGf0SWzSABBH5Lme8P1Xzr4Zqdqxp7Zadvue1h7xeCCm2zzF5E9XVnPz6LcnBkJSqTApBYV5oLt8J14hjnubANgTzKd9coy8UhlI6L2nmcHs9WGwaKgWORGJoWgLu4+B9FjN+vKmv/v3I3Vl7usQjyIPrcn+K03LRQNi+0Z10MWr+LlLQThRWP559lNj/IRHr5+Thy+Xr3cF/nMt7W5beWNsHjZb+3A55W755qSloDJzTn4MRRWuTG6l23PQ9jmdKCDPgKe24eHCVFb6n9OY6/np0IvItQBwtsL6EOGNiOQh0wqH2FI1hVyNA4rA2F7a82Fra72lhg3roe5P4YpyLcwcwPPAS69W5uyTmJCJ5cuaW2ktyOSXSUOhzRYepIRIepghAK5XSyD5XTyXGb+m9xDfPudszOgKH6/81AIYjEMQ9+249pxsGqJxwPq9mYSaRZFw1ZtWaPJpFqXTRk1er9mkSadcAQFCt+02n6dk006n+dvGIHaJ/HDPAmi3Y0PEz1KhRC9w3gUahH5Lw+HLRyvYZPyL6bwKNQD9F3ajhE5SgeOzUcgXI0jsN55pQt9hAcZ4BHoR6w6/ThEJXDeub04QiUo3Ec+jNH8ZrThyNQDs9v+O84RUdGMrfhP234LzhdMAfF4sDfiyC1+nI1nVQt8PbPK7vLap1VdhfWOqfsLuxxRtlcVi5T+2/uQvlbhh/LxT/uMpYAMjwAGSsAGWsAGRsAGe4iACwKYFAAewKYc+su4tVdxJu7iHcHEfujuIZ+GkfTAbI0lbWylfWnOpEbZDIsf4nLrUq2E4qNsEqG24JTV9I1POzCIB9a3v4HFqG/qA8DMNyootpl9nIu/lB+VLe1jd1tbtekwpBBfR8aOYVqcxyrEdoMSI3Qur7UMBRMu1GYUtAxAwYHw2DApkBqhtatm4bRYNp5wpSCjhkwOBhGAzYFUjM87iE0DAbTRhOGDHSMgEDBMBSQGaAb4TMtL2qDu46sEX7NYSq8HzmYd8ApsGA7W/SB2JsrTFDiuaB+ghTP9d4TpDhu3K0pyT9JnFEMMeI/gR/8hR7FPaTCCyMxBvvAy5wK3uBDjwvJUMycCo9ZqAdnFlzwhieaZEiGaxZceAxDPYCz4II3hNEkQzKks+DCYxjiQZ45FbyBjh4XkmGfORU6s+AMBJ9R2Hs6W9PCa/GsR4m+u7E9L7xmx3qc6Pv82vPCa/urx4m85601LbwWuM+UgGavBgTiT2OpQTHmswYRdSa2RrprtfotbIt/bYufxgjb7szWu6059MkpWzeVN2WI4qdhJ7rsNpjsbBqBGTSAxWEH1J0WhJxhkzB+evMwHsFcNCw3oLa/utTG5sunU92mumByc2e3eZowFwT0lEHXS8Vdfu+o/LCbBiPxUz2z9PQQOxxjEZyLASmWu4DZQXVn1iU3tig3nfl6ZVR3VW5DQE8Zeb0Or11+W1R+2H2dkfipHlx6eohNqLEIzsWAFGvqwOygGmjrkjPrHa1KLAato0HIKcOuV5B22AlMdshtrnHYAfV0BiGH1wIbid48jEewWQeWG1Cz7JratxT5uZxcRd08I/N9lsTRp7SWcC54urTweAhYugrwXAWsXAWsXQVsXAVsXQXYHrc6B7k0bNuKh/HooumModOwFREEUBOdVq2IIICaaDRpxcNw1eMo0kTm1stKtYw4OqYyd8japZTPUJTvvsdd4/0j4PCyW0fC4YW3jojDC38cGbeWXdYWUSG8wMgyQFH7ROhdQdYrpUZuOjg1OycqPjoRCUrn+S6EU7ODA41NfQ2Dtm3e+Nj0TfMOT6Z3u4i+adZ8bPqm2aCSMQyaIxuZvmF8GC7KG6D0LePxsembZoVKxjBmBBuZvmEO4Fwc0syWjUzfMK+YXAwDRnJx6ZvlZEbFveNNI+caBolDeV5dFXQQ5Yys+kakpYvIkYuRnGSO3I/kJHP4miQDkXEcuvmzlDCH3TAQZKC2v4xxYVowB6EDtkI+xoZpTQ2EDtgi2hgblil4CDJQc+5B6Nf9AbsPdv1ne3GJr1FuPUOtJdzDFL7CFL4GE14/6FhGH5LuoUpfoUqHs3udQ7DsPiTdQ5W+QpUOZ/cqQ2GZfUC4hyl8hSnc0uY3GRZikNelDEDsFw/6IBgrUyYokLpgrE2ZoEDqgrA6ZQDirAnOqtCUcLdloSnpbutCU9KdFoYa4UmchKLcxyR+y2znB0VFHB3lx/IlKfJi8UlGueqU3fJXdylxUQMvsIDvZTc4rselsMek8IpL4RWTwmsuhddMCm+4FN4wKbzlUnjLpPArl8Kv2ArPZpcBBh+HbQaWdLD2GSDQcdhoYMCGYqcBAh2HrQZ2bLD2GsCzcdhsMEGGercBAh2H7QZ2bLD2G8CzcdhwoE+GYscBPBuHLQdWZLD2HICTcdh00HDR3XXw/wGswIOtbHMFAA==";
    private static final Map<String, String[]> NEIGHBOR_ROWS = loadNeighborRows();
    private static final Map<String, NeighborAuthority> NEIGHBOR_AUTHORITIES =
            loadNeighborAuthorities();
    private static final Map<String, NeighborAuthority> NEIGHBOR_SHAPE_CLOSURE =
            loadNeighborShapeClosure();
    private static final Map<String, String> SUPPORT_MASKS = loadSupportMasks();
    private static final Map<String, String> SUPPORT_SHAPE_MASKS = loadSupportShapeMasks();
    private static final Map<String, SupportAuthority> SUPPORT_AUTHORITIES =
            loadSupportAuthorities();

    private Mc263PostprocessResolver() {}

    /** Authenticated vanilla neighbor predicates for one exact WebCraft carrier state. */
    public static final class NeighborAuthority {
        private final boolean sameWoodFence;
        private final boolean connectionException;
        private final int sturdyFaces;
        private final int gateConnections;
        private final int fenceConnections;
        private final int wallConnections;
        private final int wallTallFaces;
        private final boolean wallPostCovered;
        private final boolean wallPostOverride;
        private final int paneConnections;
        private final String stairFacing;
        private final String stairHalf;
        private final int multifaceSupports;
        private final boolean rigidRailSupport;

        private NeighborAuthority(String[] row) {
            sameWoodFence = bit(row[3]);
            connectionException = bit(row[4]);
            sturdyFaces = mask(row[5]);
            gateConnections = mask(row[6]);
            fenceConnections = mask(row[7]);
            wallConnections = mask(row[8]);
            wallTallFaces = mask(row[9]);
            wallPostCovered = bit(row[10]);
            wallPostOverride = bit(row[11]);
            paneConnections = mask(row[12]);
            String[] stair = row[13].equals("-") ? new String[0] : row[13].split(",", -1);
            if (stair.length != 0 && stair.length != 2) {
                throw new IllegalStateException("malformed POST stair input");
            }
            stairFacing = stair.length == 0 ? null : stair[0];
            stairHalf = stair.length == 0 ? null : stair[1];
            multifaceSupports = mask(row[14]);
            rigidRailSupport = bit(row[15]);
        }

        /** Stable identity of the official facts this authority carries, for the guard tests. */
        String signature() {
            return sameWoodFence + "|" + connectionException + "|" + sturdyFaces + "|"
                    + gateConnections + "|" + fenceConnections + "|" + wallConnections + "|"
                    + wallTallFaces + "|" + wallPostCovered + "|" + wallPostOverride + "|"
                    + paneConnections + "|" + stairFacing + "|" + stairHalf + "|"
                    + multifaceSupports + "|" + rigidRailSupport;
        }

        public boolean sameWoodFence() { return sameWoodFence; }
        public boolean connectionException() { return connectionException; }
        public boolean faceSturdy(Direction face) { return has(sturdyFaces, face); }
        public boolean gateConnects(Direction face) { return has(gateConnections, face); }
        public boolean fenceConnects(Direction face) { return has(fenceConnections, face); }
        public boolean wallConnects(Direction face) { return has(wallConnections, face); }
        public boolean wallTall(Direction side) { return has(wallTallFaces, side); }
        public boolean wallPostCovered() { return wallPostCovered; }
        public boolean wallPostOverride() { return wallPostOverride; }
        public boolean paneConnects(Direction face) { return has(paneConnections, face); }
        public boolean isStairs() { return stairFacing != null; }
        public String stairFacing() { return stairFacing; }
        public String stairHalf() { return stairHalf; }
        public boolean multifaceSupports(Direction face) {
            return has(multifaceSupports, face);
        }
        public boolean rigidRailSupport() { return rigidRailSupport; }

        private static boolean has(int mask, Direction direction) {
            return (mask & 1 << direction.ordinal()) != 0;
        }

        private static boolean bit(String value) {
            if (value.equals("0")) return false;
            if (value.equals("1")) return true;
            throw new IllegalStateException("malformed POST neighbor bit: " + value);
        }

        private static int mask(String value) {
            try {
                return Integer.parseUnsignedInt(value, 16);
            } catch (NumberFormatException invalid) {
                throw new IllegalStateException("malformed POST neighbor mask: " + value,
                        invalid);
            }
        }
    }

    /**
     * Authenticated vanilla support closure for one exact POST subject state: the official
     * {@code canSurvive} predicate class plus the official {@code updateShape} verdicts for a
     * supported and a removed support. Everything here is transcribed from the published
     * official transcript; no predicate is reconstructed in production code.
     *
     * <p>POST-EV-6 extends the closure to every catalog state whose block sits in an official
     * neighbour-dependent plant hierarchy. Those rows additionally carry the scheduled block
     * ticks the official {@code updateShape} emits on each seam, and — for the lower half of an
     * official double plant — the paired-half verdict. A subject whose survival is not decided
     * by the DOWN seam alone never becomes a row, so it keeps failing closed at POST.</p>
     */
    public static final class SupportAuthority {
        private final int supportClass;
        private final boolean always;
        private final boolean never;
        private final String supportedResult;
        private final String removedResult;
        private final boolean nonDownIdentity;

        /** Authenticated scheduled tick for one seam, or {@code null} when none is scheduled. */
        public record Tick(String key, int delay, String priority) {}

        private final Tick downSupportedTick;
        private final Tick downRemovedTick;
        private final Tick seamSupportedTick;
        private final Tick seamRemovedTick;
        private final String pairedState;
        private final String pairMatchResult;
        private final String pairMismatchResult;

        private final boolean legacy;
        private final boolean paired;

        private SupportAuthority(String[] row) {
            legacy = row[0].equals("P");
            paired = row[0].equals("D");
            downSupportedTick = legacy ? null : parseTick(row[5]);
            downRemovedTick = legacy ? null : parseTick(row[6]);
            seamSupportedTick = legacy ? null : parseTick(row[7]);
            seamRemovedTick = legacy ? null : parseTick(row[8]);
            pairedState = paired ? row[9] : null;
            pairMatchResult = paired ? row[10] : null;
            pairMismatchResult = paired ? row[11] : null;
            String classification = row[2];
            always = classification.equals("ALWAYS");
            never = classification.equals("NEVER");
            int index = -1;
            for (int candidate = 0;
                    candidate < Mc263PostSupportClosureData.SUPPORT_CLASS_NAMES.length;
                    candidate++) {
                if (Mc263PostSupportClosureData.SUPPORT_CLASS_NAMES[candidate]
                        .equals(classification)) {
                    index = candidate;
                }
            }
            if (index < 0 && !always && !never) {
                throw new IllegalStateException(
                        "unclassified POST support predicate: " + classification);
            }
            supportClass = index;
            supportedResult = row[3];
            removedResult = row[4];
            if (legacy) {
                nonDownIdentity = row[5].equals("1");
                if (!row[5].equals("0") && !nonDownIdentity) {
                    throw new IllegalStateException("malformed POST support identity bit");
                }
            } else {
                nonDownIdentity = !paired;
            }
        }

        private static Tick parseTick(String spec) {
            if (spec.equals("0/-1/-/-")) return null;
            String[] fields = spec.split("/", -1);
            if (fields.length != 4 || !fields[0].equals("1")) {
                throw new IllegalStateException("unauthenticated POST tick grammar: " + spec);
            }
            return new Tick(fields[3], Integer.parseInt(fields[1]), fields[2]);
        }

        /** Official block tick for the DOWN seam, or {@code null} when none is scheduled. */
        public Tick downTick(boolean supported) {
            return supported ? downSupportedTick : downRemovedTick;
        }

        /** Official block tick for every non-DOWN seam, or {@code null} when none. */
        public Tick seamTick(boolean supported) {
            return supported ? seamSupportedTick : seamRemovedTick;
        }

        /**
         * True when every non-DOWN seam reproduces the DOWN verdict, which is what the official
         * direction-independent vegetation {@code updateShape} does. False for the published
         * POST-EV-4 rows, whose seam identity is recorded separately, and for double plants.
         */
        public boolean seamMirrorsSupport() { return !legacy && !paired; }

        /** Paired half of an official double plant, or {@code null} when this is not one. */
        public String pairedState() { return pairedState; }

        /** Official verdict when the paired half is present. */
        public String pairMatchResult() { return pairMatchResult; }

        /** Official verdict when the paired half is absent. */
        public String pairMismatchResult() { return pairMismatchResult; }

        /** Official {@code canSurvive} verdict for this subject over the given DOWN neighbour. */
        public boolean survivesOn(Mc263FeatureBlockState below) {
            if (always) return true;
            if (never) return false;
            return supportClassSurvives(below, supportClass);
        }

        /** Official updateShape verdict when the DOWN support satisfies {@code canSurvive}. */
        public String supportedResult() { return supportedResult; }

        /** Official updateShape verdict when the DOWN support is removed. */
        public String removedResult() { return removedResult; }

        /** True when every non-DOWN official updateShape step is the identity. */
        public boolean nonDownIdentity() { return nonDownIdentity; }
    }

    /* --------------------------------------------------------------------------------- *
     * POST-EV-7: the published neighbour-dependent closures for the world-reading vegetation
     * families. Every table here is a verbatim copy of a receipt row; an unpublished key has no
     * verdict and the production lane fails closed on it.
     * --------------------------------------------------------------------------------- */

    private static final Map<String, String> EV7_FACTS = loadEv7Facts();
    private static final Map<String, String> EV7_TABLES = loadEv7Tables();
    private static final Map<String, Ev7Family> EV7_FAMILIES = loadEv7Families();
    private static final Map<String, Ev8Family> EV8_FAMILIES = loadEv8Families();
    private static final Map<String, Ev8Verdict> EV8_VERDICTS = loadEv8Verdicts();
    private static final Map<String, Ev8Family> EV8_STATE_FAMILIES = loadEv8StateFamilies();
    private static final Map<String, Ev9Authority> EV9_AUTHORITIES = loadEv9Authorities();
    private static final Map<String, String> EV9_SUPPORT = loadEv9Support();
    private static final Map<String, String> EV9_SHAPE_SUPPORT = loadEv9ShapeSupport();
    private static final Map<String, Ev10Authority> EV10_AUTHORITIES = loadEv10Authorities();
    private static final List<AttachmentGeneration> ATTACHMENT_GENERATIONS = List.of(
            new AttachmentGeneration("E12H", "E12S", "E12R",
                    Mc263PostSupportClosureData.EV12_FAMILY_ROW_COUNT,
                    Mc263PostSupportClosureData.EV12_SUBJECT_ROW_COUNT,
                    Mc263PostSupportClosureData.EV12_RESIDUAL_ROW_COUNT),
            new AttachmentGeneration("E13H", "E13S", "E13R",
                    Mc263PostSupportClosureData.EV13_FAMILY_ROW_COUNT,
                    Mc263PostSupportClosureData.EV13_SUBJECT_ROW_COUNT,
                    Mc263PostSupportClosureData.EV13_RESIDUAL_ROW_COUNT),
            new AttachmentGeneration("E16COH", "E16COS", "E16COR",
                    Mc263PostSupportClosureData.EV16_CORAL_FAMILY_ROW_COUNT,
                    Mc263PostSupportClosureData.EV16_CORAL_SUBJECT_ROW_COUNT,
                    Mc263PostSupportClosureData.EV16_CORAL_RESIDUAL_ROW_COUNT));

    private static final Map<String, AttachmentAuthority> ATTACHMENT_AUTHORITIES =
            loadAttachmentAuthorities();

    /** Number of leading fields that identify one published POST-EV-7 table row. */
    private static int ev7KeyLength(String prefix) {
        return switch (prefix) {
            case "V" -> 5; case "VS" -> 2; case "MS" -> 3; case "MD" -> 7; case "MT" -> 2;
            case "FH" -> 2; case "FS" -> 5; default -> -1;
        };
    }

    /** One official POST-EV-7 family: its subject block, support seam and canSurvive predicate. */
    public record Ev7Family(String label, String blockKey, String supportSpec, String predicate) {}

    /** One final POST-EV-8 family and its authenticated exact-state cardinality. */
    public static final class Ev8Family {
        private final String label;
        private final String blockKey;
        private final int stateCount;
        private Ev8Family(String label, String blockKey, int stateCount) {
            this.label = label;
            this.blockKey = blockKey;
            this.stateCount = stateCount;
        }
        public String label() { return label; }
        public String blockKey() { return blockKey; }
        public int stateCount() { return stateCount; }
    }

    /** One official POST-EV-8 updateShape verdict, scheduled tick and RNG transcript. */
    public static final class Ev8Verdict {
        private final String resultState;
        private final String tickSpec;
        private final String randomSpec;
        private Ev8Verdict(String resultState, String tickSpec, String randomSpec) {
            this.resultState = resultState;
            this.tickSpec = tickSpec;
            this.randomSpec = randomSpec;
        }
        public String resultState() { return resultState; }
        public String tickSpec() { return tickSpec; }
        public String randomSpec() { return randomSpec; }
    }

    /** One official POST-EV-9 result and scheduled-tick transcript. */
    public record Ev9Verdict(String resultState, String tickSpec) {}

    /** Catalog-derived POST-EV-10 pairing for one official waterlogged property tuple. */
    public static final class Ev10Authority {
        private final String blockKey;
        private final String dryState;
        private final String waterloggedState;
        private final String waterTickCounts;
        private final String identityDirections;

        private Ev10Authority(String[] row) {
            if (row.length != 6 || !row[0].equals("E10P")) {
                throw new IllegalStateException("malformed POST-EV-10 pair row");
            }
            blockKey = row[1];
            dryState = row[2];
            waterloggedState = row[3];
            waterTickCounts = row[4];
            identityDirections = row[5];
            if (waterTickCounts.length() != Direction.values().length
                    || waterTickCounts.chars().anyMatch(value -> value < '0' || value > '9')
                    || identityDirections.length() != Direction.values().length
                    || identityDirections.chars().anyMatch(value -> value != '0'
                            && value != '1')) {
                throw new IllegalStateException("malformed POST-EV-10 direction closure");
            }
            if (!dryState.replace("waterlogged=false", "waterlogged=*").equals(
                    waterloggedState.replace("waterlogged=true", "waterlogged=*"))) {
                throw new IllegalStateException("POST-EV-10 paired-state drift");
            }
        }

        public String blockKey() { return blockKey; }
        public String dryState() { return dryState; }
        public String waterloggedState() { return waterloggedState; }
        public boolean schedulesWaterTick(Direction direction) {
            return waterTickCounts.charAt(Objects.requireNonNull(
                    direction, "POST-EV-10 direction").ordinal()) != '0';
        }
        public boolean isIdentity(Direction direction) {
            return identityDirections.charAt(Objects.requireNonNull(
                    direction, "POST-EV-10 direction").ordinal()) == '1';
        }
        public boolean isWaterlogged(Mc263FeatureBlockState state) {
            String exact = Objects.requireNonNull(state, "POST-EV-10 state").exactState();
            if (!exact.equals(dryState) && !exact.equals(waterloggedState)) {
                throw new IllegalArgumentException("state is outside POST-EV-10 pair: " + exact);
            }
            return exact.equals(waterloggedState);
        }
    }

    /** Total attached-state support authority for one exact subject state. */
    public static final class Ev9Authority {
        private final int ordinal;
        private final String family;
        private final Direction supportDirection;
        private final Ev9Verdict[] supported;
        private final Ev9Verdict[] removed;

        private Ev9Authority(String[] row) {
            if (row.length != 29 || !row[0].equals("E9S")) {
                throw new IllegalStateException("malformed POST-EV-9 subject row");
            }
            ordinal = Integer.parseInt(row[1]);
            family = row[2];
            supportDirection = Direction.valueOf(row[4].toUpperCase(java.util.Locale.ROOT));
            supported = new Ev9Verdict[Direction.values().length];
            removed = new Ev9Verdict[Direction.values().length];
            for (int index = 0; index < Direction.values().length; index++) {
                int offset = 5 + index * 4;
                supported[index] = new Ev9Verdict(row[offset], row[offset + 1]);
                removed[index] = new Ev9Verdict(row[offset + 2], row[offset + 3]);
            }
        }

        public int ordinal() { return ordinal; }
        public String family() { return family; }
        public Direction supportDirection() { return supportDirection; }
        public Ev9Verdict verdict(Direction direction, boolean survives) {
            return (survives ? supported : removed)[direction.ordinal()];
        }
    }

    /**
     * Registry-derived attachment authority for one exact attached state.
     *
     * <p>The official {@code updateShape} of an attached family -- the POST-EV-12 coral wall fans
     * and the POST-EV-13 amethyst buds and clusters alike -- clears the block on the attachment
     * seam alone, and only when the official {@code canSurvive} fails; the published row carries
     * the probed attachment direction and, per seam, the result state and scheduled tick for both
     * verdicts. The published tick spec may carry a count above one: the waterlogged decorator
     * and the block rule both request the same source-water tick, and the resolver journal keeps
     * the first winner.</p>
     */
    public static final class AttachmentAuthority {
        private final int ordinal;
        private final String blockKey;
        private final Direction supportDirection;
        private final boolean carried;
        private final Ev9Verdict[] supported;
        private final Ev9Verdict[] removed;

        private AttachmentAuthority(String[] row, String subjectTag) {
            if (row.length != 30 || !row[0].equals(subjectTag)) {
                throw new IllegalStateException("malformed " + subjectTag + " subject row");
            }
            ordinal = Integer.parseInt(row[1]);
            blockKey = row[2];
            supportDirection = Direction.valueOf(row[4].toUpperCase(java.util.Locale.ROOT));
            carried = row[5].equals("1");
            supported = new Ev9Verdict[Direction.values().length];
            removed = new Ev9Verdict[Direction.values().length];
            for (int index = 0; index < Direction.values().length; index++) {
                int offset = 6 + index * 4;
                supported[index] = new Ev9Verdict(row[offset], row[offset + 1]);
                removed[index] = new Ev9Verdict(row[offset + 2], row[offset + 3]);
            }
        }

        public int ordinal() { return ordinal; }
        public String blockKey() { return blockKey; }
        public Direction supportDirection() { return supportDirection; }
        /** True when the WebCraft exact-state catalog can carry this subject. */
        public boolean carried() { return carried; }
        public Ev9Verdict verdict(Direction direction, boolean survives) {
            return (survives ? supported : removed)[Objects.requireNonNull(
                    direction, "attachment direction").ordinal()];
        }
    }

    /**
     * One published attachment generation: its row tags and its own published cardinality. The
     * counts come from the generated closure data, never from a literal beside the table.
     */
    private record AttachmentGeneration(String familyTag, String subjectTag, String residualTag,
            int familyRows, int subjectRows, int residualRows) {}

    /**
     * Loads every registry-derived attachment generation into one production table.
     *
     * <p>Each generation is a family/subject/residual triple in the packed closure, and the
     * cardinality of each triple is bound to its own published counts, so a generation cannot
     * silently borrow another's rows. The generations partition the exact-state space, so one
     * merged map answers production and a state published twice fails closed at class load.</p>
     */
    private static Map<String, AttachmentAuthority> loadAttachmentAuthorities() {
        Map<String, AttachmentAuthority> values = new LinkedHashMap<>();
        for (AttachmentGeneration generation : ATTACHMENT_GENERATIONS) {
            Map<String, Integer> familyStates = new LinkedHashMap<>();
            Set<String> residuals = new LinkedHashSet<>();
            int subjects = 0;
            int nextOrdinal = 0;
            for (String line : Mc263PostSupportClosureData.rows()) {
                String[] row = line.split("\\t", -1);
                if (row[0].equals(generation.familyTag())) {
                    if (row.length != 7
                            || familyStates.put(row[1], Integer.parseInt(row[3])) != null) {
                        throw new ExceptionInInitializerError(
                                "malformed " + generation.familyTag() + " family row");
                    }
                } else if (row[0].equals(generation.subjectTag())) {
                    AttachmentAuthority authority =
                            new AttachmentAuthority(row, generation.subjectTag());
                    if (authority.ordinal() != nextOrdinal++
                            || !familyStates.containsKey(authority.blockKey())
                            || !row[3].startsWith(authority.blockKey() + "[")) {
                        throw new ExceptionInInitializerError(
                                "malformed " + generation.subjectTag() + " subject order");
                    }
                    subjects++;
                    // Only a subject the carrier can hold becomes a production authority; the
                    // rest stay published for the enumeration and never answer a lookup.
                    if (authority.carried() && values.put(row[3], authority) != null) {
                        throw new ExceptionInInitializerError(
                                "duplicate attachment subject " + row[3]);
                    }
                } else if (row[0].equals(generation.residualTag())) {
                    if (row.length != 6 || !familyStates.containsKey(row[1])
                            || !residuals.add(row[2])
                            || Integer.parseInt(row[3]) + Integer.parseInt(row[4])
                                    + Integer.parseInt(row[5]) < 1) {
                        throw new ExceptionInInitializerError(
                                "malformed " + generation.residualTag() + " residual row");
                    }
                }
            }
            if (familyStates.size() != generation.familyRows()
                    || subjects != generation.subjectRows()
                    || residuals.size() != generation.residualRows()
                    || subjects + residuals.size()
                            != familyStates.values().stream().mapToInt(Integer::intValue).sum()) {
                throw new ExceptionInInitializerError(
                        generation.familyTag() + " closure cardinality drift");
            }
        }
        return Map.copyOf(values);
    }

    /* --------------------------------------------------------------------------------- *
     * POST-EV-14: the official paired-half closure. A paired-half family has a seam toward its
     * own other half that TRANSFERS state -- the official {@code updateShape} answers the
     * neighbour's own state re-halved onto this half -- so the verdict is not the two-class
     * supported/removed factorisation an attachment generation publishes. The published subject
     * row therefore carries the ordinal of its opposite-half twin, and the transfer is a lookup
     * in that published table, never a formula.
     * --------------------------------------------------------------------------------- */

    /** One published paired-half generation: its row tags and its own published cardinality. */
    private record PairedHalfGeneration(String familyTag, String subjectTag, String residualTag,
            int familyRows, int subjectRows, int residualRows) {}

    private static final List<PairedHalfGeneration> PAIRED_HALF_GENERATIONS = List.of(
            new PairedHalfGeneration("E14H", "E14S", "E14R",
                    Mc263PostSupportClosureData.EV14_FAMILY_ROW_COUNT,
                    Mc263PostSupportClosureData.EV14_SUBJECT_ROW_COUNT,
                    Mc263PostSupportClosureData.EV14_RESIDUAL_ROW_COUNT));

    /**
     * One official paired-half subject.
     *
     * <p>Three published columns answer every seam: the paired seam carries the transfer verdict
     * and the mismatch verdict, the DOWN seam of a lower half carries the official
     * {@code canSurvive} verdict factored on the published sturdy-face column, and every
     * remaining seam is the identity. An upper half has no DOWN column at all -- DOWN is its
     * paired seam -- and asking for one fails closed.</p>
     */
    public static final class PairedHalfAuthority {
        private final int ordinal;
        private final String blockKey;
        private final String exactState;
        private final String half;
        private final Direction pairedSeam;
        private final int twinOrdinal;
        private final boolean carried;
        private final String identityTick;
        private final Ev9Verdict pairMismatch;
        private final String transferTick;
        private final Ev9Verdict supported;
        private final Ev9Verdict removed;

        private PairedHalfAuthority(String[] row, String subjectTag) {
            if (row.length != 16 || !row[0].equals(subjectTag)) {
                throw new IllegalStateException("malformed " + subjectTag + " subject row");
            }
            ordinal = Integer.parseInt(row[1]);
            blockKey = row[2];
            exactState = row[3];
            half = row[4];
            pairedSeam = Direction.valueOf(row[5].toUpperCase(java.util.Locale.ROOT));
            twinOrdinal = Integer.parseInt(row[6]);
            carried = row[7].equals("1");
            identityTick = row[8];
            pairMismatch = new Ev9Verdict(row[9], row[10]);
            transferTick = row[11];
            boolean lower = row[12].equals("-");
            if (lower != (pairedSeam == Direction.DOWN)) {
                throw new IllegalStateException(
                        "malformed " + subjectTag + " DOWN support column for " + exactState);
            }
            supported = lower ? null : new Ev9Verdict(row[12], row[13]);
            removed = lower ? null : new Ev9Verdict(row[14], row[15]);
        }

        public int ordinal() { return ordinal; }
        public String blockKey() { return blockKey; }
        public String exactState() { return exactState; }
        /** Published half of this subject; the paired neighbour is the one that differs. */
        public String half() { return half; }
        /** The seam that points at this subject's own other half. */
        public Direction pairedSeam() { return pairedSeam; }
        /** Ordinal of the same state with the opposite half: the official transfer target. */
        public int twinOrdinal() { return twinOrdinal; }
        /** True when the WebCraft exact-state catalog can carry this subject. */
        public boolean carried() { return carried; }
        /** Published tick of every seam that is neither the paired seam nor the DOWN support. */
        public String identityTick() { return identityTick; }
        /** Official verdict when the paired seam holds anything but the other half. */
        public Ev9Verdict pairMismatch() { return pairMismatch; }
        /** Published tick of the official paired-half state transfer. */
        public String transferTick() { return transferTick; }

        /** Official DOWN verdict of a lower half; an upper half has no such column. */
        public Ev9Verdict downVerdict(boolean survives) {
            Ev9Verdict verdict = survives ? supported : removed;
            if (verdict == null) {
                throw new IllegalStateException(
                        "no authenticated POST paired-half DOWN column for " + exactState);
            }
            return verdict;
        }
    }

    private static final Map<String, PairedHalfAuthority> PAIRED_HALF_SUBJECTS =
            loadPairedHalfSubjects();
    private static final List<String> PAIRED_HALF_STATES = loadPairedHalfStates();
    private static final Set<String> PAIRED_HALF_FAMILIES = loadPairedHalfFamilies();

    /**
     * Loads every published paired-half generation into one production table.
     *
     * <p>Every subject is indexed, carried or not: a neighbour the carrier cannot hold can never
     * reach production, and a family state that is published but absent from this table would
     * make the transfer unanswerable, so the loader binds the whole published enumeration and the
     * family set separately gates which block keys the lane may answer for at all.</p>
     */
    private static Map<String, PairedHalfAuthority> loadPairedHalfSubjects() {
        Map<String, PairedHalfAuthority> values = new LinkedHashMap<>();
        for (PairedHalfGeneration generation : PAIRED_HALF_GENERATIONS) {
            Map<String, Integer> familyStates = new LinkedHashMap<>();
            Set<String> residuals = new LinkedHashSet<>();
            int subjects = 0;
            int nextOrdinal = 0;
            for (String line : Mc263PostSupportClosureData.rows()) {
                String[] row = line.split("\\t", -1);
                if (row[0].equals(generation.familyTag())) {
                    if (row.length != 7
                            || familyStates.put(row[1], Integer.parseInt(row[3])) != null) {
                        throw new ExceptionInInitializerError(
                                "malformed " + generation.familyTag() + " family row");
                    }
                } else if (row[0].equals(generation.subjectTag())) {
                    PairedHalfAuthority authority =
                            new PairedHalfAuthority(row, generation.subjectTag());
                    if (authority.ordinal() != nextOrdinal++
                            || !familyStates.containsKey(authority.blockKey())
                            || !row[3].startsWith(authority.blockKey() + "[")
                            || !row[3].contains("half=" + authority.half())
                            || values.put(row[3], authority) != null) {
                        throw new ExceptionInInitializerError(
                                "malformed " + generation.subjectTag() + " subject order");
                    }
                    subjects++;
                } else if (row[0].equals(generation.residualTag())) {
                    if (row.length != 6 || !familyStates.containsKey(row[1])
                            || !residuals.add(row[2])
                            || Integer.parseInt(row[3]) + Integer.parseInt(row[4])
                                    + Integer.parseInt(row[5]) < 1) {
                        throw new ExceptionInInitializerError(
                                "malformed " + generation.residualTag() + " residual row");
                    }
                }
            }
            if (familyStates.size() != generation.familyRows()
                    || subjects != generation.subjectRows()
                    || residuals.size() != generation.residualRows()
                    || subjects + residuals.size()
                            != familyStates.values().stream().mapToInt(Integer::intValue).sum()) {
                throw new ExceptionInInitializerError(
                        generation.familyTag() + " closure cardinality drift");
            }
        }
        return Map.copyOf(values);
    }

    /**
     * Indexes the published subjects by ordinal so the official half projection is a lookup.
     *
     * <p>The twin map has to be a fixed-point-free involution over those ordinals, and each twin
     * has to be the same block with the opposite half: that is what makes the published table the
     * official {@code setValue(HALF, half)} projection rather than a second copy of it.</p>
     */
    private static List<String> loadPairedHalfStates() {
        String[] states = new String[PAIRED_HALF_SUBJECTS.size()];
        for (PairedHalfAuthority authority : PAIRED_HALF_SUBJECTS.values()) {
            states[authority.ordinal()] = authority.exactState();
        }
        for (PairedHalfAuthority authority : PAIRED_HALF_SUBJECTS.values()) {
            String twin = states[authority.twinOrdinal()];
            if (twin == null) {
                throw new ExceptionInInitializerError(
                        "POST paired-half twin ordinal is unpublished for "
                                + authority.exactState());
            }
            PairedHalfAuthority paired = PAIRED_HALF_SUBJECTS.get(twin);
            if (paired == null || paired.twinOrdinal() != authority.ordinal()
                    || paired.half().equals(authority.half())
                    || !paired.blockKey().equals(authority.blockKey())
                    || !twin.replace("half=" + paired.half(), "half=*").equals(
                            authority.exactState().replace("half=" + authority.half(), "half=*"))) {
                throw new ExceptionInInitializerError(
                        "POST paired-half twin map drift for " + authority.exactState());
            }
        }
        return List.of(states);
    }

    private static Set<String> loadPairedHalfFamilies() {
        Set<String> values = new LinkedHashSet<>();
        for (PairedHalfGeneration generation : PAIRED_HALF_GENERATIONS) {
            for (String line : Mc263PostSupportClosureData.rows()) {
                String[] row = line.split("\\t", -1);
                if (row[0].equals(generation.familyTag())) values.add(row[1]);
            }
        }
        return Set.copyOf(values);
    }

    /** Official paired-half authority for a carrier state, or {@code null} outside the lane. */
    public static PairedHalfAuthority pairedHalfAuthority(Mc263FeatureBlockState state) {
        PairedHalfAuthority authority = PAIRED_HALF_SUBJECTS.get(
                Objects.requireNonNull(state, "paired-half subject").exactState());
        return authority != null && authority.carried() ? authority : null;
    }

    /**
     * Official paired-half authority of a neighbour, or {@code null} when it is not one.
     *
     * <p>A state whose block is a published paired-half family but which the closure never
     * published fails closed instead of being treated as a stranger: the transfer verdict for it
     * is unauthenticated.</p>
     */
    public static PairedHalfAuthority pairedHalfNeighbour(Mc263FeatureBlockState state) {
        String exact = Objects.requireNonNull(state, "paired-half neighbour").exactState();
        PairedHalfAuthority authority = PAIRED_HALF_SUBJECTS.get(exact);
        if (authority == null && PAIRED_HALF_FAMILIES.contains(state.blockKey())) {
            throw new IllegalStateException(
                    "no authenticated POST paired-half authority for " + exact);
        }
        return authority;
    }

    /** Published state of one paired-half ordinal; an unpublished ordinal fails closed. */
    public static String pairedHalfState(int ordinal) {
        if (ordinal < 0 || ordinal >= PAIRED_HALF_STATES.size()) {
            throw new IllegalArgumentException("POST paired-half ordinal out of range");
        }
        return PAIRED_HALF_STATES.get(ordinal);
    }

    /* --------------------------------------------------------------------------------- *
     * POST-EV-15: two official closures no earlier generation can express.
     *
     * <p>The first is a seam identity closure: a registry-derived family whose official
     * {@code updateShape} was proven to be the identity on every seam over the whole published
     * candidate universe, with one published tick column and a uniform {@code canSurvive} bit.
     * The published verdict is the subject's own state, read as data, so production answers from
     * one row instead of falling through to the shape-independent rule.</p>
     *
     * <p>The second is a double-plant closure. Its official verdict is a function of the block
     * BELOW rather than of the seam: the subject clears unless it survives where it stands, and
     * the seam that points at its own other half additionally clears unless that half is there.
     * The survival column is not republished -- the subject row names the POST-EV-6 support class
     * whose official {@code canSurvive} predicate the generation proved it shares, so the lane
     * reads the pinned support table.</p>
     * --------------------------------------------------------------------------------- */

    /** One published seam identity generation: its row tags and its own published cardinality. */
    private record SeamIdentityGeneration(String familyTag, String subjectTag, String residualTag,
            String aliasTag, int familyRows, int subjectRows, int residualRows, int aliasRows) {}

    private static final List<SeamIdentityGeneration> SEAM_IDENTITY_GENERATIONS = List.of(
            new SeamIdentityGeneration("E15H", "E15S", "E15R", "E15A",
                    Mc263PostSupportClosureData.EV15_FAMILY_ROW_COUNT,
                    Mc263PostSupportClosureData.EV15_SUBJECT_ROW_COUNT,
                    Mc263PostSupportClosureData.EV15_RESIDUAL_ROW_COUNT, 0),
            new SeamIdentityGeneration("E16H", "E16S", "E16R", "E16A",
                    Mc263PostSupportClosureData.EV16_FAMILY_ROW_COUNT,
                    Mc263PostSupportClosureData.EV16_SUBJECT_ROW_COUNT,
                    Mc263PostSupportClosureData.EV16_RESIDUAL_ROW_COUNT,
                    Mc263PostSupportClosureData.EV16_ALIAS_ROW_COUNT),
            new SeamIdentityGeneration("E16CH", "E16CS", "E16CR", "E16CA",
                    Mc263PostSupportClosureData.EV16_CHAIN_FAMILY_ROW_COUNT,
                    Mc263PostSupportClosureData.EV16_CHAIN_SUBJECT_ROW_COUNT,
                    Mc263PostSupportClosureData.EV16_CHAIN_RESIDUAL_ROW_COUNT,
                    Mc263PostSupportClosureData.EV16_CHAIN_ALIAS_ROW_COUNT),
            new SeamIdentityGeneration("E17H", "E17S", "E17R", "E17A",
                    Mc263PostSupportClosureData.EV17_FAMILY_ROW_COUNT,
                    Mc263PostSupportClosureData.EV17_SUBJECT_ROW_COUNT,
                    Mc263PostSupportClosureData.EV17_RESIDUAL_ROW_COUNT,
                    Mc263PostSupportClosureData.EV17_ALIAS_ROW_COUNT),
            new SeamIdentityGeneration("E18H", "E18S", "E18R", "E18A",
                    Mc263PostSupportClosureData.EV18_FAMILY_ROW_COUNT,
                    Mc263PostSupportClosureData.EV18_SUBJECT_ROW_COUNT,
                    Mc263PostSupportClosureData.EV18_RESIDUAL_ROW_COUNT,
                    Mc263PostSupportClosureData.EV18_ALIAS_ROW_COUNT));

    /** One published double-plant generation: its row tags and its own published cardinality. */
    private record DoublePlantGeneration(String familyTag, String subjectTag, String residualTag,
            int familyRows, int subjectRows, int residualRows) {}

    private static final List<DoublePlantGeneration> DOUBLE_PLANT_GENERATIONS = List.of(
            new DoublePlantGeneration("E15PH", "E15PS", "E15PR",
                    Mc263PostSupportClosureData.EV15_PLANT_FAMILY_ROW_COUNT,
                    Mc263PostSupportClosureData.EV15_PLANT_SUBJECT_ROW_COUNT,
                    Mc263PostSupportClosureData.EV15_PLANT_RESIDUAL_ROW_COUNT));

    /**
     * One official seam identity subject: one published verdict answers every seam.
     *
     * <p>The published {@code canSurvive} bit is carried as well, because a family that ever
     * fails its own survival predicate could not be answered by a single identity row and the
     * loader refuses to bind it.</p>
     */
    public static final class SeamIdentityAuthority {
        private final int ordinal;
        private final String blockKey;
        private final String exactState;
        private final boolean carried;
        private final Ev9Verdict verdict;
        private final boolean survives;

        private SeamIdentityAuthority(String[] row, String subjectTag) {
            if (row.length != 8 || !row[0].equals(subjectTag)) {
                throw new IllegalStateException("malformed " + subjectTag + " subject row");
            }
            ordinal = Integer.parseInt(row[1]);
            blockKey = row[2];
            exactState = row[3];
            carried = row[4].equals("1");
            verdict = new Ev9Verdict(row[5], row[6]);
            survives = row[7].equals("1");
            if (!row[4].equals("0") && !row[4].equals("1")
                    || !row[7].equals("0") && !row[7].equals("1")
                    || !exactState.equals(verdict.resultState())) {
                throw new IllegalStateException(
                        "malformed " + subjectTag + " identity verdict for " + exactState);
            }
            // A family that can fail its own survival predicate is not answerable by one
            // identity row, so the loader refuses it instead of publishing a partial lane.
            if (!survives) {
                throw new IllegalStateException(
                        "unsupported " + subjectTag + " identity subject " + exactState);
            }
        }

        public int ordinal() { return ordinal; }
        public String blockKey() { return blockKey; }
        public String exactState() { return exactState; }
        /** True when the WebCraft exact-state catalog can carry this subject. */
        public boolean carried() { return carried; }
        /** The published verdict of every seam: the subject's own state and its tick column. */
        public Ev9Verdict verdict() { return verdict; }
        /** The published official {@code canSurvive} verdict, uniform over every candidate. */
        public boolean survives() { return survives; }
    }

    /**
     * One official double-plant subject.
     *
     * <p>Two published columns answer every seam. The paired seam -- the one that points at this
     * subject's own other half -- clears to the published mismatch verdict unless it holds the
     * published paired state. Everything else, and the paired seam when it does hold that state,
     * is the published supported/removed verdict of the official {@code canSurvive} predicate,
     * read from the pinned POST-EV-6 support class this subject shares.</p>
     */
    public static final class DoublePlantAuthority {
        private final int ordinal;
        private final String blockKey;
        private final String exactState;
        private final String half;
        private final Direction pairedSeam;
        private final boolean carried;
        private final int supportClass;
        private final String pairedState;
        private final Ev9Verdict pairMismatch;
        private final Ev9Verdict supported;
        private final Ev9Verdict removed;

        private DoublePlantAuthority(String[] row, String subjectTag) {
            if (row.length != 16 || !row[0].equals(subjectTag)) {
                throw new IllegalStateException("malformed " + subjectTag + " subject row");
            }
            ordinal = Integer.parseInt(row[1]);
            blockKey = row[2];
            exactState = row[3];
            half = row[4];
            pairedSeam = Direction.valueOf(row[5].toUpperCase(java.util.Locale.ROOT));
            carried = row[6].equals("1");
            // The class is bound by its published NAME and cross-checked against the published
            // ordinal, so a reordered support table cannot silently move this column.
            supportClass = supportClassOrdinal(row[8]);
            pairedState = row[9];
            pairMismatch = new Ev9Verdict(row[10], row[11]);
            supported = new Ev9Verdict(row[12], row[13]);
            removed = new Ev9Verdict(row[14], row[15]);
            if (supportClass != Integer.parseInt(row[7])
                    || !exactState.startsWith(blockKey + "[")
                    || !exactState.contains("half=" + half)
                    || !supported.resultState().equals(exactState)
                    || pairedState.equals(exactState)) {
                throw new IllegalStateException(
                        "malformed " + subjectTag + " double-plant row for " + exactState);
            }
        }

        public int ordinal() { return ordinal; }
        public String blockKey() { return blockKey; }
        public String exactState() { return exactState; }
        /** Published half of this subject; the paired state is the one that differs. */
        public String half() { return half; }
        /** The seam that points at this subject's own other half. */
        public Direction pairedSeam() { return pairedSeam; }
        /** True when the WebCraft exact-state catalog can carry this subject. */
        public boolean carried() { return carried; }
        /** Published POST-EV-6 support class ordinal of this subject's survival predicate. */
        public int supportClass() { return supportClass; }
        /** The exact state the paired seam has to hold, published as data. */
        public String pairedState() { return pairedState; }
        /** Official verdict when the paired seam holds anything but the published paired state. */
        public Ev9Verdict pairMismatch() { return pairMismatch; }

        /** Official verdict of every remaining seam, keyed on the published support column. */
        public Ev9Verdict supportVerdict(boolean survives) {
            return survives ? supported : removed;
        }
    }

    private static final Map<String, SeamIdentityAuthority> SEAM_IDENTITY_SUBJECTS =
            loadSeamIdentitySubjects();
    private static final Map<String, DoublePlantAuthority> DOUBLE_PLANT_SUBJECTS =
            loadDoublePlantSubjects();

    /** Loads every published seam identity generation into one production table. */
    private static Map<String, SeamIdentityAuthority> loadSeamIdentitySubjects() {
        Map<String, SeamIdentityAuthority> values = new LinkedHashMap<>();
        for (SeamIdentityGeneration generation : SEAM_IDENTITY_GENERATIONS) {
            Map<String, Integer> familyStates = new LinkedHashMap<>();
            Set<String> residuals = new LinkedHashSet<>();
            int subjects = 0;
            int nextOrdinal = 0;
            for (String line : Mc263PostSupportClosureData.rows()) {
                String[] row = line.split("\\t", -1);
                if (row[0].equals(generation.familyTag())) {
                    if (row.length != 7
                            || familyStates.put(row[1], Integer.parseInt(row[3])) != null) {
                        throw new ExceptionInInitializerError(
                                "malformed " + generation.familyTag() + " family row");
                    }
                } else if (row[0].equals(generation.subjectTag())) {
                    SeamIdentityAuthority authority =
                            new SeamIdentityAuthority(row, generation.subjectTag());
                    if (authority.ordinal() != nextOrdinal++
                            || !familyStates.containsKey(authority.blockKey())
                            || values.put(row[3], authority) != null) {
                        throw new ExceptionInInitializerError(
                                "malformed " + generation.subjectTag() + " subject order");
                    }
                    subjects++;
                } else if (row[0].equals(generation.residualTag())) {
                    if (row.length != 6 || !familyStates.containsKey(row[1])
                            || !residuals.add(row[2])) {
                        throw new ExceptionInInitializerError(
                                "malformed " + generation.residualTag() + " residual row");
                    }
                }
            }
            if (familyStates.size() != generation.familyRows()
                    || subjects != generation.subjectRows()
                    || residuals.size() != generation.residualRows()
                    || subjects + residuals.size()
                            != familyStates.values().stream().mapToInt(Integer::intValue).sum()) {
                throw new ExceptionInInitializerError(
                        generation.familyTag() + " closure cardinality drift");
            }
            // A family the catalog spells under an older official name is published under that
            // spelling too. The alias row restates one published subject's identity verdict in the
            // carrier's own spelling, so the production carrier -- which never holds the newer
            // official name -- reads the lane that authenticated it.
            int aliases = 0;
            for (String line : Mc263PostSupportClosureData.rows()) {
                String[] row = line.split("\\t", -1);
                if (!row[0].equals(generation.aliasTag())) continue;
                if (row.length != 6 || !row[3].equals(row[1])) {
                    throw new ExceptionInInitializerError(
                            "malformed " + generation.aliasTag() + " alias row");
                }
                SeamIdentityAuthority official = values.get(row[2]);
                int open = row[1].indexOf('[');
                if (official == null || open <= 0) {
                    throw new ExceptionInInitializerError(
                            "unpublished " + generation.aliasTag() + " alias subject " + row[2]);
                }
                String[] restated = {generation.subjectTag(), Integer.toString(official.ordinal()),
                        row[1].substring(0, open), row[1], row[5], row[3], row[4],
                        official.survives() ? "1" : "0"};
                if (values.put(row[1],
                        new SeamIdentityAuthority(restated, generation.subjectTag())) != null) {
                    throw new ExceptionInInitializerError(
                            generation.aliasTag() + " alias shadows a published subject");
                }
                aliases++;
            }
            if (aliases != generation.aliasRows()) {
                throw new ExceptionInInitializerError(
                        generation.aliasTag() + " alias cardinality drift");
            }
        }
        return Map.copyOf(values);
    }

    /**
     * Loads every published double-plant generation into one production table.
     *
     * <p>A carried subject's published paired state has to be a published subject too, or the
     * lane could not answer the seam it names.</p>
     */
    private static Map<String, DoublePlantAuthority> loadDoublePlantSubjects() {
        Map<String, DoublePlantAuthority> values = new LinkedHashMap<>();
        for (DoublePlantGeneration generation : DOUBLE_PLANT_GENERATIONS) {
            Map<String, Integer> familyStates = new LinkedHashMap<>();
            Set<String> residuals = new LinkedHashSet<>();
            int subjects = 0;
            int nextOrdinal = 0;
            for (String line : Mc263PostSupportClosureData.rows()) {
                String[] row = line.split("\\t", -1);
                if (row[0].equals(generation.familyTag())) {
                    if (row.length != 7
                            || familyStates.put(row[1], Integer.parseInt(row[3])) != null) {
                        throw new ExceptionInInitializerError(
                                "malformed " + generation.familyTag() + " family row");
                    }
                } else if (row[0].equals(generation.subjectTag())) {
                    DoublePlantAuthority authority =
                            new DoublePlantAuthority(row, generation.subjectTag());
                    if (authority.ordinal() != nextOrdinal++
                            || !familyStates.containsKey(authority.blockKey())
                            || values.put(row[3], authority) != null) {
                        throw new ExceptionInInitializerError(
                                "malformed " + generation.subjectTag() + " subject order");
                    }
                    subjects++;
                } else if (row[0].equals(generation.residualTag())) {
                    if (row.length != 6 || !familyStates.containsKey(row[1])
                            || !residuals.add(row[2])) {
                        throw new ExceptionInInitializerError(
                                "malformed " + generation.residualTag() + " residual row");
                    }
                }
            }
            if (familyStates.size() != generation.familyRows()
                    || subjects != generation.subjectRows()
                    || residuals.size() != generation.residualRows()
                    || subjects + residuals.size()
                            != familyStates.values().stream().mapToInt(Integer::intValue).sum()) {
                throw new ExceptionInInitializerError(
                        generation.familyTag() + " closure cardinality drift");
            }
        }
        for (DoublePlantAuthority authority : values.values()) {
            DoublePlantAuthority paired = values.get(authority.pairedState());
            if (paired == null || paired.half().equals(authority.half())
                    || !paired.blockKey().equals(authority.blockKey())
                    || !paired.pairedState().equals(authority.exactState())) {
                throw new ExceptionInInitializerError(
                        "POST double-plant paired state drift for " + authority.exactState());
            }
        }
        return Map.copyOf(values);
    }

    /** Official seam identity authority for a carrier state, or {@code null} outside the lane. */
    public static SeamIdentityAuthority seamIdentityAuthority(Mc263FeatureBlockState state) {
        SeamIdentityAuthority authority = SEAM_IDENTITY_SUBJECTS.get(
                Objects.requireNonNull(state, "seam identity subject").exactState());
        return authority != null && authority.carried() ? authority : null;
    }

    /** Official double-plant authority for a carrier state, or {@code null} outside the lane. */
    public static DoublePlantAuthority doublePlantAuthority(Mc263FeatureBlockState state) {
        DoublePlantAuthority authority = DOUBLE_PLANT_SUBJECTS.get(
                Objects.requireNonNull(state, "double-plant subject").exactState());
        return authority != null && authority.carried() ? authority : null;
    }

    /* --------------------------------------------------------------------------------- *
     * POST-EV-16: three official closures no earlier generation can express, published
     * through one lane table per kind. The two seam identity lanes and the attachment lane
     * are new rows in the tables above, because the generation that authenticated them is
     * the one those tables already read.
     * --------------------------------------------------------------------------------- */

    /** One published horizontal paired-half generation: its row tags and its cardinality. */
    private record PairedHorizontalGeneration(String familyTag, String subjectTag,
            String residualTag, int familyRows, int subjectRows, int residualRows) {}

    private static final List<PairedHorizontalGeneration> PAIRED_HORIZONTAL_GENERATIONS =
            List.of(new PairedHorizontalGeneration("E16PH", "E16PS", "E16PR",
                    Mc263PostSupportClosureData.EV16_PAIRED_FAMILY_ROW_COUNT,
                    Mc263PostSupportClosureData.EV16_PAIRED_SUBJECT_ROW_COUNT,
                    Mc263PostSupportClosureData.EV16_PAIRED_RESIDUAL_ROW_COUNT));

    /** One published contained-fluid generation: its row tags and its cardinality. */
    private record ContainedFluidGeneration(String familyTag, String subjectTag,
            String residualTag, int familyRows, int subjectRows, int residualRows) {}

    private static final List<ContainedFluidGeneration> CONTAINED_FLUID_GENERATIONS =
            List.of(new ContainedFluidGeneration("E16FH", "E16FS", "E16FR",
                    Mc263PostSupportClosureData.EV16_FLUID_FAMILY_ROW_COUNT,
                    Mc263PostSupportClosureData.EV16_FLUID_SUBJECT_ROW_COUNT,
                    Mc263PostSupportClosureData.EV16_FLUID_RESIDUAL_ROW_COUNT));

    /** One published cross-connection generation: its row tags and its cardinality. */
    private record CrossConnectionGeneration(String familyTag, String subjectTag,
            String residualTag, String neighborTag, int familyRows, int subjectRows,
            int residualRows, int neighborRows) {}

    private static final List<CrossConnectionGeneration> CROSS_CONNECTION_GENERATIONS =
            List.of(new CrossConnectionGeneration("E16PNH", "E16PNS", "E16PNR", "E16PNN",
                    Mc263PostSupportClosureData.EV16_PANE_FAMILY_ROW_COUNT,
                    Mc263PostSupportClosureData.EV16_PANE_SUBJECT_ROW_COUNT,
                    Mc263PostSupportClosureData.EV16_PANE_RESIDUAL_ROW_COUNT,
                    Mc263PostSupportClosureData.EV16_PANE_NEIGHBOR_ROW_COUNT));

    /**
     * One official horizontal paired-half subject.
     *
     * <p>The paired seam is published rather than derived: it differs per state because the
     * pairing axis is the subject's own facing. That seam answers the published result of the
     * partner it holds -- the pairs are published as data, one row per partner -- and the
     * published air verdict for anything else. Every other seam is the identity.</p>
     */
    public static final class PairedHorizontalAuthority {
        private final int ordinal;
        private final String blockKey;
        private final String exactState;
        private final boolean carried;
        private final Direction pairedSeam;
        private final String support;
        private final Ev9Verdict identity;
        private final Ev9Verdict mismatch;
        private final List<String> projection;
        private final Map<String, Ev9Verdict> partners;

        private PairedHorizontalAuthority(String[] row, String subjectTag) {
            if (row.length < 16 || (row.length - 13) % 3 != 0 || !row[0].equals(subjectTag)) {
                throw new IllegalStateException("malformed " + subjectTag + " subject row");
            }
            ordinal = Integer.parseInt(row[1]);
            blockKey = row[2];
            exactState = row[3];
            carried = row[4].equals("1");
            pairedSeam = Direction.valueOf(row[5].toUpperCase(java.util.Locale.ROOT));
            support = row[6];
            identity = new Ev9Verdict(row[7], row[8]);
            mismatch = new Ev9Verdict(row[9], row[10]);
            projection = row[11].equals("-") ? List.of() : List.of(row[11].split(","));
            int published = Integer.parseInt(row[12]);
            Map<String, Ev9Verdict> values = new LinkedHashMap<>();
            for (int index = 13; index < row.length; index += 3) {
                if (values.put(row[index], new Ev9Verdict(row[index + 1], row[index + 2]))
                        != null) {
                    throw new IllegalStateException(
                            "duplicate " + subjectTag + " partner for " + exactState);
                }
            }
            partners = Map.copyOf(values);
            if (partners.size() != published
                    || !row[4].equals("0") && !row[4].equals("1")
                    || !exactState.startsWith(blockKey + "[")
                    || !identity.resultState().equals(exactState)
                    || !mismatch.resultState().equals("minecraft:air")
                    || !support.startsWith("CLASS/") && !support.startsWith("UNIFORM/")) {
                throw new IllegalStateException(
                        "malformed " + subjectTag + " paired row for " + exactState);
            }
        }

        public int ordinal() { return ordinal; }
        public String blockKey() { return blockKey; }
        public String exactState() { return exactState; }
        /** True when the WebCraft exact-state catalog can carry this subject. */
        public boolean carried() { return carried; }
        /** The seam that points at this subject's own other half. */
        public Direction pairedSeam() { return pairedSeam; }
        /** Published support column: a POST-EV-6 class or the observed uniform verdict. */
        public String support() { return support; }
        /** Official verdict of every seam that is not the paired one. */
        public Ev9Verdict identity() { return identity; }
        /** Official verdict when the paired seam holds anything but a published partner. */
        public Ev9Verdict mismatch() { return mismatch; }
        /** The properties the official transfer takes from the partner, published as evidence. */
        public List<String> projection() { return projection; }

        /** Official verdict of the paired seam over one neighbour, or the mismatch verdict. */
        public Ev9Verdict pairedVerdict(String neighborState) {
            Ev9Verdict verdict = partners.get(
                    Objects.requireNonNull(neighborState, "paired neighbour"));
            return verdict != null ? verdict : mismatch;
        }
    }

    /**
     * One official contained-fluid subject: the fluid this state carries and its own tick delay.
     *
     * <p>The production contained-fluid pass had one authenticated answer per fluid SOURCE; a
     * flowing state carries a different fluid with its own published key, so the lane answers
     * from the state's published row instead of failing closed on it.</p>
     */
    public static final class ContainedFluidAuthority {
        private final int ordinal;
        private final String blockKey;
        private final String exactState;
        private final boolean carried;
        private final String fluidKey;
        private final boolean source;
        private final int amount;
        private final int tickDelay;

        private ContainedFluidAuthority(String[] row, String subjectTag) {
            if (row.length != 9 || !row[0].equals(subjectTag)) {
                throw new IllegalStateException("malformed " + subjectTag + " subject row");
            }
            ordinal = Integer.parseInt(row[1]);
            blockKey = row[2];
            exactState = row[3];
            carried = row[4].equals("1");
            fluidKey = row[5];
            source = row[6].equals("1");
            amount = Integer.parseInt(row[7]);
            tickDelay = Integer.parseInt(row[8]);
            if (!row[4].equals("0") && !row[4].equals("1")
                    || !row[6].equals("0") && !row[6].equals("1")
                    || !fluidKey.startsWith("minecraft:") || tickDelay < 0
                    || !exactState.equals(blockKey) && !exactState.startsWith(blockKey + "[")) {
                throw new IllegalStateException(
                        "malformed " + subjectTag + " contained-fluid row for " + exactState);
            }
        }

        public int ordinal() { return ordinal; }
        public String blockKey() { return blockKey; }
        public String exactState() { return exactState; }
        /** True when the WebCraft exact-state catalog can carry this subject. */
        public boolean carried() { return carried; }
        /** The official fluid this state contains, published as its registry key. */
        public String fluidKey() { return fluidKey; }
        /** True when the contained fluid is the official source state. */
        public boolean source() { return source; }
        /** The official fluid amount this state carries. */
        public int amount() { return amount; }
        /** The official tick delay of the contained fluid, measured against the official level. */
        public int tickDelay() { return tickDelay; }
    }

    /**
     * One official cross-connection subject.
     *
     * <p>A vertical seam is the identity. A horizontal seam answers the subject with its own
     * published connection property rewritten from the published neighbour predicate, so the
     * production step reads two tables -- this row and the folded neighbour row -- rather than
     * recomputing an attachment rule.</p>
     */
    public static final class CrossConnectionAuthority {
        private final int ordinal;
        private final String blockKey;
        private final String exactState;
        private final boolean carried;
        private final Ev9Verdict vertical;
        private final String horizontalTick;
        private final Map<Direction, String> properties;
        private final Map<Direction, String> connected;
        private final Map<Direction, String> unconnected;

        private CrossConnectionAuthority(String[] row, String subjectTag) {
            if (row.length != 24 || !row[0].equals(subjectTag)) {
                throw new IllegalStateException("malformed " + subjectTag + " subject row");
            }
            ordinal = Integer.parseInt(row[1]);
            blockKey = row[2];
            exactState = row[3];
            carried = row[4].equals("1");
            vertical = new Ev9Verdict(row[5], row[6]);
            horizontalTick = row[7];
            Map<Direction, String> names = new LinkedHashMap<>();
            Map<Direction, String> yes = new LinkedHashMap<>();
            Map<Direction, String> no = new LinkedHashMap<>();
            for (int index = 8; index < row.length; index += 4) {
                Direction seam = Direction.valueOf(row[index].toUpperCase(java.util.Locale.ROOT));
                if (names.put(seam, row[index + 1]) != null) {
                    throw new IllegalStateException(
                            "duplicate " + subjectTag + " seam for " + exactState);
                }
                yes.put(seam, row[index + 2]);
                no.put(seam, row[index + 3]);
            }
            properties = Map.copyOf(names);
            connected = Map.copyOf(yes);
            unconnected = Map.copyOf(no);
            if (!row[4].equals("0") && !row[4].equals("1")
                    || !exactState.startsWith(blockKey + "[")
                    || !vertical.resultState().equals(exactState)
                    || properties.size() != 4) {
                throw new IllegalStateException(
                        "malformed " + subjectTag + " cross-connection row for " + exactState);
            }
        }

        public int ordinal() { return ordinal; }
        public String blockKey() { return blockKey; }
        public String exactState() { return exactState; }
        /** True when the WebCraft exact-state catalog can carry this subject. */
        public boolean carried() { return carried; }
        /** Official verdict of a vertical seam: the subject's own state. */
        public Ev9Verdict vertical() { return vertical; }

        /** Official verdict of one horizontal seam under the published neighbour predicate. */
        public Ev9Verdict horizontalVerdict(Direction seam, boolean connects) {
            String result = (connects ? connected : unconnected).get(
                    Objects.requireNonNull(seam, "cross-connection seam"));
            if (result == null) {
                throw new IllegalStateException(
                        "no authenticated cross-connection seam " + seam + " for " + exactState);
            }
            return new Ev9Verdict(result, horizontalTick);
        }
    }

    /**
     * The published horizontal seam order of the cross-connection tables, which is the order the
     * official generation swept and published its per-seam bits in.
     */
    private static final List<Direction> CROSS_HORIZONTAL_ORDER =
            List.of(Direction.WEST, Direction.EAST, Direction.NORTH, Direction.SOUTH);

    /** One published cross-connection neighbour: its per-seam predicate, published as data. */
    public static final class CrossConnectionNeighbor {
        private final String exactState;
        private final String connects;
        private final String whenSturdy;
        private final String whenPlain;

        private CrossConnectionNeighbor(String[] row, String neighborTag) {
            if (row.length != 6 || !row[0].equals(neighborTag)) {
                throw new IllegalStateException("malformed " + neighborTag + " neighbour row");
            }
            exactState = row[1];
            connects = row[2];
            whenSturdy = row[4];
            whenPlain = row[5];
            if (row[2].length() != 4 || row[3].length() != 4
                    || !whenSturdy.matches("[01-]") || !whenPlain.matches("[01-]")) {
                throw new IllegalStateException(
                        "malformed " + neighborTag + " predicate for " + exactState);
            }
            // Every published bit has to be the case it names, or the row could answer a seam
            // it was never observed for.
            for (int face = 0; face < 4; face++) {
                String expected = row[3].charAt(face) == '1' ? whenSturdy : whenPlain;
                if (expected.equals("-") || row[2].charAt(face) != expected.charAt(0)) {
                    throw new IllegalStateException(
                            "unauthenticated " + neighborTag + " predicate for " + exactState);
                }
            }
        }

        public String exactState() { return exactState; }

        /**
         * The published predicate for one horizontal seam. The bit is read straight out of the
         * published row, so production never recomputes a face or an attachment rule; the
         * sturdy-face columns behind it are the generation's proof, checked at load.
         */
        public boolean connects(Direction seam) {
            int face = CROSS_HORIZONTAL_ORDER.indexOf(
                    Objects.requireNonNull(seam, "cross-connection seam"));
            if (face < 0) {
                throw new IllegalStateException(
                        "no authenticated cross-connection seam " + seam + " for " + exactState);
            }
            return connects.charAt(face) == '1';
        }
    }

    private static final Map<String, PairedHorizontalAuthority> PAIRED_HORIZONTAL_SUBJECTS =
            loadPairedHorizontalSubjects();
    private static final Map<String, ContainedFluidAuthority> CONTAINED_FLUID_SUBJECTS =
            loadContainedFluidSubjects();
    private static final Map<String, CrossConnectionAuthority> CROSS_CONNECTION_SUBJECTS =
            loadCrossConnectionSubjects();
    private static final Map<String, CrossConnectionNeighbor> CROSS_CONNECTION_NEIGHBORS =
            loadCrossConnectionNeighbors();

    /** Loads every published horizontal paired-half generation into one production table. */
    private static Map<String, PairedHorizontalAuthority> loadPairedHorizontalSubjects() {
        Map<String, PairedHorizontalAuthority> values = new LinkedHashMap<>();
        for (PairedHorizontalGeneration generation : PAIRED_HORIZONTAL_GENERATIONS) {
            Map<String, Integer> familyStates = new LinkedHashMap<>();
            Set<String> residuals = new LinkedHashSet<>();
            int subjects = 0;
            int nextOrdinal = 0;
            for (String line : Mc263PostSupportClosureData.rows()) {
                String[] row = line.split("\\t", -1);
                if (row[0].equals(generation.familyTag())) {
                    if (row.length != 7
                            || familyStates.put(row[1], Integer.parseInt(row[3])) != null) {
                        throw new ExceptionInInitializerError(
                                "malformed " + generation.familyTag() + " family row");
                    }
                } else if (row[0].equals(generation.subjectTag())) {
                    PairedHorizontalAuthority authority =
                            new PairedHorizontalAuthority(row, generation.subjectTag());
                    if (authority.ordinal() != nextOrdinal++
                            || !familyStates.containsKey(authority.blockKey())
                            || values.put(row[3], authority) != null) {
                        throw new ExceptionInInitializerError(
                                "malformed " + generation.subjectTag() + " subject order");
                    }
                    subjects++;
                } else if (row[0].equals(generation.residualTag())) {
                    if (row.length != 6 || !familyStates.containsKey(row[1])
                            || !residuals.add(row[2])) {
                        throw new ExceptionInInitializerError(
                                "malformed " + generation.residualTag() + " residual row");
                    }
                }
            }
            if (familyStates.size() != generation.familyRows()
                    || subjects != generation.subjectRows()
                    || residuals.size() != generation.residualRows()
                    || subjects + residuals.size()
                            != familyStates.values().stream().mapToInt(Integer::intValue).sum()) {
                throw new ExceptionInInitializerError(
                        generation.familyTag() + " closure cardinality drift");
            }
        }
        return Map.copyOf(values);
    }

    /** Loads every published contained-fluid generation into one production table. */
    private static Map<String, ContainedFluidAuthority> loadContainedFluidSubjects() {
        Map<String, ContainedFluidAuthority> values = new LinkedHashMap<>();
        for (ContainedFluidGeneration generation : CONTAINED_FLUID_GENERATIONS) {
            Map<String, Integer> familyStates = new LinkedHashMap<>();
            Set<String> residuals = new LinkedHashSet<>();
            int subjects = 0;
            int nextOrdinal = 0;
            for (String line : Mc263PostSupportClosureData.rows()) {
                String[] row = line.split("\\t", -1);
                if (row[0].equals(generation.familyTag())) {
                    if (row.length != 7
                            || familyStates.put(row[1], Integer.parseInt(row[3])) != null) {
                        throw new ExceptionInInitializerError(
                                "malformed " + generation.familyTag() + " family row");
                    }
                } else if (row[0].equals(generation.subjectTag())) {
                    ContainedFluidAuthority authority =
                            new ContainedFluidAuthority(row, generation.subjectTag());
                    if (authority.ordinal() != nextOrdinal++
                            || !familyStates.containsKey(authority.blockKey())
                            || values.put(row[3], authority) != null) {
                        throw new ExceptionInInitializerError(
                                "malformed " + generation.subjectTag() + " subject order");
                    }
                    subjects++;
                } else if (row[0].equals(generation.residualTag())) {
                    if (row.length != 6 || !familyStates.containsKey(row[1])
                            || !residuals.add(row[2])) {
                        throw new ExceptionInInitializerError(
                                "malformed " + generation.residualTag() + " residual row");
                    }
                }
            }
            if (familyStates.size() != generation.familyRows()
                    || subjects != generation.subjectRows()
                    || residuals.size() != generation.residualRows()
                    || subjects + residuals.size()
                            != familyStates.values().stream().mapToInt(Integer::intValue).sum()) {
                throw new ExceptionInInitializerError(
                        generation.familyTag() + " closure cardinality drift");
            }
        }
        return Map.copyOf(values);
    }

    /** Loads every published cross-connection generation into one production table. */
    private static Map<String, CrossConnectionAuthority> loadCrossConnectionSubjects() {
        Map<String, CrossConnectionAuthority> values = new LinkedHashMap<>();
        for (CrossConnectionGeneration generation : CROSS_CONNECTION_GENERATIONS) {
            Map<String, Integer> familyStates = new LinkedHashMap<>();
            Set<String> residuals = new LinkedHashSet<>();
            int subjects = 0;
            int nextOrdinal = 0;
            for (String line : Mc263PostSupportClosureData.rows()) {
                String[] row = line.split("\\t", -1);
                if (row[0].equals(generation.familyTag())) {
                    if (row.length != 7
                            || familyStates.put(row[1], Integer.parseInt(row[3])) != null) {
                        throw new ExceptionInInitializerError(
                                "malformed " + generation.familyTag() + " family row");
                    }
                } else if (row[0].equals(generation.subjectTag())) {
                    CrossConnectionAuthority authority =
                            new CrossConnectionAuthority(row, generation.subjectTag());
                    if (authority.ordinal() != nextOrdinal++
                            || !familyStates.containsKey(authority.blockKey())
                            || values.put(row[3], authority) != null) {
                        throw new ExceptionInInitializerError(
                                "malformed " + generation.subjectTag() + " subject order");
                    }
                    subjects++;
                } else if (row[0].equals(generation.residualTag())) {
                    if (row.length != 6 || !familyStates.containsKey(row[1])
                            || !residuals.add(row[2])) {
                        throw new ExceptionInInitializerError(
                                "malformed " + generation.residualTag() + " residual row");
                    }
                }
            }
            if (familyStates.size() != generation.familyRows()
                    || subjects != generation.subjectRows()
                    || residuals.size() != generation.residualRows()
                    || subjects + residuals.size()
                            != familyStates.values().stream().mapToInt(Integer::intValue).sum()) {
                throw new ExceptionInInitializerError(
                        generation.familyTag() + " closure cardinality drift");
            }
        }
        return Map.copyOf(values);
    }

    /** Loads the published cross-connection neighbour predicate into one production table. */
    private static Map<String, CrossConnectionNeighbor> loadCrossConnectionNeighbors() {
        Map<String, CrossConnectionNeighbor> values = new LinkedHashMap<>();
        for (CrossConnectionGeneration generation : CROSS_CONNECTION_GENERATIONS) {
            int neighbors = 0;
            for (String line : Mc263PostSupportClosureData.rows()) {
                String[] row = line.split("\\t", -1);
                if (!row[0].equals(generation.neighborTag())) continue;
                CrossConnectionNeighbor neighbor =
                        new CrossConnectionNeighbor(row, generation.neighborTag());
                if (values.put(neighbor.exactState(), neighbor) != null) {
                    throw new ExceptionInInitializerError(
                            "duplicate " + generation.neighborTag() + " neighbour row");
                }
                neighbors++;
            }
            if (neighbors != generation.neighborRows()) {
                throw new ExceptionInInitializerError(
                        generation.neighborTag() + " neighbour cardinality drift");
            }
        }
        return Map.copyOf(values);
    }

    /** Official horizontal paired-half authority for a carrier state, or {@code null} outside. */
    public static PairedHorizontalAuthority pairedHorizontalAuthority(
            Mc263FeatureBlockState state) {
        PairedHorizontalAuthority authority = PAIRED_HORIZONTAL_SUBJECTS.get(
                Objects.requireNonNull(state, "paired-horizontal subject").exactState());
        return authority != null && authority.carried() ? authority : null;
    }

    /** Official contained-fluid authority for a carrier state, or {@code null} outside the lane. */
    public static ContainedFluidAuthority containedFluidAuthority(Mc263FeatureBlockState state) {
        ContainedFluidAuthority authority = CONTAINED_FLUID_SUBJECTS.get(
                Objects.requireNonNull(state, "contained-fluid subject").exactState());
        return authority != null && authority.carried() ? authority : null;
    }

    /** Official cross-connection authority for a carrier state, or {@code null} outside the lane. */
    public static CrossConnectionAuthority crossConnectionAuthority(Mc263FeatureBlockState state) {
        CrossConnectionAuthority authority = CROSS_CONNECTION_SUBJECTS.get(
                Objects.requireNonNull(state, "cross-connection subject").exactState());
        return authority != null && authority.carried() ? authority : null;
    }

    /** Published cross-connection predicate for one neighbour, or {@code null} when unpublished. */
    public static CrossConnectionNeighbor crossConnectionNeighbor(Mc263FeatureBlockState state) {
        return CROSS_CONNECTION_NEIGHBORS.get(
                Objects.requireNonNull(state, "cross-connection neighbour").exactState());
    }

    /** One published seam-property generation: its row tags and its own published cardinality. */
    private record SeamPropertyGeneration(String familyTag, String subjectTag, String residualTag,
            String neighborTag, int familyRows, int subjectRows, int residualRows,
            int neighborRows) {}

    private static final List<SeamPropertyGeneration> SEAM_PROPERTY_GENERATIONS =
            List.of(new SeamPropertyGeneration("E18GH", "E18GS", "E18GR", "E18GN",
                    Mc263PostSupportClosureData.EV18_GATE_FAMILY_ROW_COUNT,
                    Mc263PostSupportClosureData.EV18_GATE_SUBJECT_ROW_COUNT,
                    Mc263PostSupportClosureData.EV18_GATE_RESIDUAL_ROW_COUNT,
                    Mc263PostSupportClosureData.EV18_GATE_NEIGHBOR_ROW_COUNT));

    /**
     * One official seam-property subject: each seam either answers one published state whatever
     * the neighbour holds, or rewrites one of the subject's own properties from the published
     * neighbour predicate.
     */
    public static final class SeamPropertyAuthority {
        private final int ordinal;
        private final String blockKey;
        private final String exactState;
        private final boolean carried;
        private final String tickSpec;
        private final Map<Direction, String> identities;
        private final Map<Direction, String> properties;
        private final Map<Direction, String> rewritten;
        private final Map<Direction, String> kept;

        private SeamPropertyAuthority(String[] row, String subjectTag) {
            if (row.length != 6 + 4 * ORDER_LENGTH || !row[0].equals(subjectTag)) {
                throw new IllegalStateException("malformed " + subjectTag + " subject row");
            }
            ordinal = Integer.parseInt(row[1]);
            blockKey = row[2];
            exactState = row[3];
            carried = row[4].equals("1");
            tickSpec = row[5];
            Map<Direction, String> seamIdentities = new LinkedHashMap<>();
            Map<Direction, String> names = new LinkedHashMap<>();
            Map<Direction, String> yes = new LinkedHashMap<>();
            Map<Direction, String> no = new LinkedHashMap<>();
            for (int index = 6; index < row.length; index += 4) {
                Direction seam = Direction.valueOf(row[index].toUpperCase(java.util.Locale.ROOT));
                if (row[index + 1].equals("IDENTITY")) {
                    if (!row[index + 3].equals("-")
                            || seamIdentities.put(seam, row[index + 2]) != null) {
                        throw new IllegalStateException(
                                "malformed " + subjectTag + " identity seam for " + exactState);
                    }
                    continue;
                }
                if (names.put(seam, row[index + 1]) != null) {
                    throw new IllegalStateException(
                            "duplicate " + subjectTag + " seam for " + exactState);
                }
                yes.put(seam, row[index + 2]);
                no.put(seam, row[index + 3]);
            }
            identities = Map.copyOf(seamIdentities);
            properties = Map.copyOf(names);
            rewritten = Map.copyOf(yes);
            kept = Map.copyOf(no);
            if (!row[4].equals("0") && !row[4].equals("1")
                    || !exactState.startsWith(blockKey + "[")
                    || identities.size() + properties.size() != ORDER_LENGTH) {
                throw new IllegalStateException(
                        "malformed " + subjectTag + " seam-property row for " + exactState);
            }
        }

        public int ordinal() { return ordinal; }
        public String blockKey() { return blockKey; }
        public String exactState() { return exactState; }
        /** True when the WebCraft exact-state catalog can carry this subject. */
        public boolean carried() { return carried; }

        /** Official verdict of one seam under the published neighbour predicate. */
        public Ev9Verdict verdict(Direction seam, boolean predicate) {
            String identity = identities.get(Objects.requireNonNull(seam, "seam-property seam"));
            if (identity != null) return new Ev9Verdict(identity, tickSpec);
            String result = (predicate ? rewritten : kept).get(seam);
            if (result == null) {
                throw new IllegalStateException(
                        "no authenticated seam-property seam " + seam + " for " + exactState);
            }
            return new Ev9Verdict(result, tickSpec);
        }

        /** True when this seam rewrites a property rather than answering one published state. */
        public boolean rewrites(Direction seam) {
            return properties.containsKey(Objects.requireNonNull(seam, "seam-property seam"));
        }
    }

    /** One published seam-property neighbour: the folded predicate every rewriting seam reads. */
    public static final class SeamPropertyNeighbor {
        private final String exactState;
        private final boolean predicate;

        private SeamPropertyNeighbor(String[] row, String neighborTag) {
            if (row.length != 4 || !row[0].equals(neighborTag)
                    || row[2].length() != ORDER_LENGTH
                    || !row[3].equals("0") && !row[3].equals("1")) {
                throw new IllegalStateException("malformed " + neighborTag + " neighbour row");
            }
            exactState = row[1];
            predicate = row[3].equals("1");
            // Every published per-seam bit is that same folded column, or the row could answer a
            // seam it was never observed for.
            for (int index = 0; index < ORDER_LENGTH; index++) {
                char bit = row[2].charAt(index);
                if (bit != '-' && bit != row[3].charAt(0)) {
                    throw new IllegalStateException(
                            "unauthenticated " + neighborTag + " predicate for " + exactState);
                }
            }
        }

        public String exactState() { return exactState; }
        /** The published predicate this neighbour presents to every rewriting seam. */
        public boolean predicate() { return predicate; }
    }

    private static final int ORDER_LENGTH = 6;

    private static final Map<String, SeamPropertyAuthority> SEAM_PROPERTY_SUBJECTS =
            loadSeamPropertySubjects();
    private static final Map<String, SeamPropertyNeighbor> SEAM_PROPERTY_NEIGHBORS =
            loadSeamPropertyNeighbors();

    /** Loads every published seam-property generation into one production table. */
    private static Map<String, SeamPropertyAuthority> loadSeamPropertySubjects() {
        Map<String, SeamPropertyAuthority> values = new LinkedHashMap<>();
        for (SeamPropertyGeneration generation : SEAM_PROPERTY_GENERATIONS) {
            Map<String, Integer> familyStates = new LinkedHashMap<>();
            Set<String> residuals = new LinkedHashSet<>();
            int subjects = 0;
            int nextOrdinal = 0;
            for (String line : Mc263PostSupportClosureData.rows()) {
                String[] row = line.split("\\t", -1);
                if (row[0].equals(generation.familyTag())) {
                    if (row.length != 7
                            || familyStates.put(row[1], Integer.parseInt(row[3])) != null) {
                        throw new ExceptionInInitializerError(
                                "malformed " + generation.familyTag() + " family row");
                    }
                } else if (row[0].equals(generation.subjectTag())) {
                    SeamPropertyAuthority authority =
                            new SeamPropertyAuthority(row, generation.subjectTag());
                    if (authority.ordinal() != nextOrdinal++
                            || !familyStates.containsKey(authority.blockKey())
                            || values.put(row[3], authority) != null) {
                        throw new ExceptionInInitializerError(
                                "malformed " + generation.subjectTag() + " subject order");
                    }
                    subjects++;
                } else if (row[0].equals(generation.residualTag())) {
                    if (row.length != 6 || !familyStates.containsKey(row[1])
                            || !residuals.add(row[2])) {
                        throw new ExceptionInInitializerError(
                                "malformed " + generation.residualTag() + " residual row");
                    }
                }
            }
            if (familyStates.size() != generation.familyRows()
                    || subjects != generation.subjectRows()
                    || residuals.size() != generation.residualRows()
                    || subjects + residuals.size()
                            != familyStates.values().stream().mapToInt(Integer::intValue).sum()) {
                throw new ExceptionInInitializerError(
                        generation.familyTag() + " closure cardinality drift");
            }
        }
        return Map.copyOf(values);
    }

    /** Loads the published seam-property neighbour predicate into one production table. */
    private static Map<String, SeamPropertyNeighbor> loadSeamPropertyNeighbors() {
        Map<String, SeamPropertyNeighbor> values = new LinkedHashMap<>();
        for (SeamPropertyGeneration generation : SEAM_PROPERTY_GENERATIONS) {
            int neighbors = 0;
            for (String line : Mc263PostSupportClosureData.rows()) {
                String[] row = line.split("\\t", -1);
                if (!row[0].equals(generation.neighborTag())) continue;
                SeamPropertyNeighbor neighbor =
                        new SeamPropertyNeighbor(row, generation.neighborTag());
                if (values.put(neighbor.exactState(), neighbor) != null) {
                    throw new ExceptionInInitializerError(
                            "duplicate " + generation.neighborTag() + " neighbour row");
                }
                neighbors++;
            }
            if (neighbors != generation.neighborRows()) {
                throw new ExceptionInInitializerError(
                        generation.neighborTag() + " neighbour cardinality drift");
            }
        }
        return Map.copyOf(values);
    }

    /** Official seam-property authority for a carrier state, or {@code null} outside the lane. */
    public static SeamPropertyAuthority seamPropertyAuthority(Mc263FeatureBlockState state) {
        SeamPropertyAuthority authority = SEAM_PROPERTY_SUBJECTS.get(
                Objects.requireNonNull(state, "seam-property subject").exactState());
        return authority != null && authority.carried() ? authority : null;
    }

    /** Published seam-property predicate for one neighbour, or {@code null} when unpublished. */
    public static SeamPropertyNeighbor seamPropertyNeighbor(Mc263FeatureBlockState state) {
        return SEAM_PROPERTY_NEIGHBORS.get(
                Objects.requireNonNull(state, "seam-property neighbour").exactState());
    }

    private static Map<String, String> loadEv7Facts() {
        Map<String, String> values = new LinkedHashMap<>();
        for (String line : Mc263PostSupportClosureData.rows()) {
            String[] row = line.split("\\t", -1);
            if (!row[0].equals("F")) continue;
            if (row.length != 3
                    || row[2].length() != Mc263PostSupportClosureData.FACT_NAMES.length
                    || values.put(row[1], row[2]) != null) {
                throw new ExceptionInInitializerError("malformed POST-EV-7 fact row");
            }
        }
        if (values.size() != Mc263PostSupportClosureData.FACT_ROW_COUNT) {
            throw new ExceptionInInitializerError("POST-EV-7 fact cardinality drift");
        }
        return Map.copyOf(values);
    }

    private static Map<String, String> loadEv7Tables() {
        Map<String, String> values = new LinkedHashMap<>();
        for (String line : Mc263PostSupportClosureData.rows()) {
            String[] row = line.split("\\t", -1);
            int keyLength = ev7KeyLength(row[0]);
            if (keyLength < 0) continue;
            if (row.length <= keyLength) {
                throw new ExceptionInInitializerError("malformed POST-EV-7 table row");
            }
            String key = String.join("\t", Arrays.copyOfRange(row, 0, keyLength));
            String value = String.join("\t", Arrays.copyOfRange(row, keyLength, row.length));
            if (values.put(key, value) != null) {
                throw new ExceptionInInitializerError("duplicate POST-EV-7 table key");
            }
        }
        if (values.size() != Mc263PostSupportClosureData.EV7_TABLE_ROW_COUNT) {
            throw new ExceptionInInitializerError("POST-EV-7 table cardinality drift");
        }
        return Map.copyOf(values);
    }

    private static Map<String, Ev7Family> loadEv7Families() {
        Map<String, Ev7Family> values = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : EV7_TABLES.entrySet()) {
            if (!entry.getKey().startsWith("FH\t")) continue;
            String label = entry.getKey().substring(3);
            String[] fields = entry.getValue().split("\\t", -1);
            if (fields.length != 5
                    || values.put(fields[0], new Ev7Family(label, fields[0], fields[1],
                            fields[2])) != null) {
                throw new ExceptionInInitializerError("malformed POST-EV-7 family header");
            }
        }
        if (values.isEmpty()) {
            throw new ExceptionInInitializerError("POST-EV-7 family headers absent");
        }
        return Map.copyOf(values);
    }

    private static Map<String, Ev8Family> loadEv8Families() {
        Map<String, Ev8Family> values = new LinkedHashMap<>();
        int rows = 0;
        boolean light = false;
        boolean random = false;
        for (String line : Mc263PostSupportClosureData.rows()) {
            String[] row = line.split("\\t", -1);
            if (row[0].equals("EL")) {
                rows++;
                light = Arrays.equals(row, new String[]{"EL", "SKY_HEIGHTMAP_COLUMN", "15",
                        "BLOCK", "0", "SKY_DARKEN", "0"});
            } else if (row[0].equals("ER")) {
                rows++;
                random = Arrays.equals(row, new String[]{"ER", "CAVE_VINES_BODY_TO_HEAD",
                        "nextInt", "25", "1"});
            } else if (row[0].equals("EH")) {
                rows++;
                if (row.length != 4) {
                    throw new ExceptionInInitializerError("malformed POST-EV-8 family row");
                }
                Ev8Family family = new Ev8Family(row[1], row[2], Integer.parseInt(row[3]));
                if (values.put(row[2], family) != null) {
                    throw new ExceptionInInitializerError("duplicate POST-EV-8 family block");
                }
            } else if (row[0].equals("EC")) {
                rows++;
            }
        }
        if (!light || !random || values.size() != 12 || rows == 0) {
            throw new ExceptionInInitializerError("POST-EV-8 authority header drift");
        }
        return Map.copyOf(values);
    }

    private static Map<String, Ev8Verdict> loadEv8Verdicts() {
        Map<String, Ev8Verdict> values = new LinkedHashMap<>();
        int ev8Rows = 0;
        for (String line : Mc263PostSupportClosureData.rows()) {
            String[] row = line.split("\\t", -1);
            if (Set.of("EL", "ER", "EH", "EC").contains(row[0])) ev8Rows++;
            if (!row[0].equals("EC")) continue;
            if (row.length != 8) {
                throw new ExceptionInInitializerError("malformed POST-EV-8 verdict row");
            }
            String key = String.join("\\t", Arrays.copyOfRange(row, 1, 5));
            Ev8Verdict verdict = new Ev8Verdict(row[5], row[6], row[7]);
            if (values.put(key, verdict) != null) {
                throw new ExceptionInInitializerError("duplicate POST-EV-8 verdict key");
            }
        }
        if (ev8Rows != Mc263PostSupportClosureData.EV8_TABLE_ROW_COUNT || values.isEmpty()) {
            throw new ExceptionInInitializerError("POST-EV-8 verdict cardinality drift");
        }
        return Map.copyOf(values);
    }

    private static Map<String, Ev8Family> loadEv8StateFamilies() {
        Map<String, Ev8Family> byLabel = new LinkedHashMap<>();
        for (Ev8Family family : EV8_FAMILIES.values()) byLabel.put(family.label(), family);
        Map<String, Ev8Family> values = new LinkedHashMap<>();
        Map<String, Set<String>> exactByFamily = new LinkedHashMap<>();
        for (String line : Mc263PostSupportClosureData.rows()) {
            String[] fields = line.split("\t", -1);
            if (!fields[0].equals("EC")) continue;
            if (fields.length != 8) {
                throw new ExceptionInInitializerError("malformed POST-EV-8 verdict row");
            }
            Ev8Family family = byLabel.get(fields[1]);
            if (family == null) {
                throw new ExceptionInInitializerError("unknown POST-EV-8 verdict family");
            }
            Ev8Family previous = values.putIfAbsent(fields[2], family);
            if (previous != null && previous != family) {
                throw new ExceptionInInitializerError("POST-EV-8 exact state crosses families");
            }
            exactByFamily.computeIfAbsent(family.label(), ignored -> new LinkedHashSet<>())
                    .add(fields[2]);
        }
        for (Ev8Family family : EV8_FAMILIES.values()) {
            if (exactByFamily.getOrDefault(family.label(), Set.of()).size()
                    != family.stateCount()) {
                throw new ExceptionInInitializerError("POST-EV-8 family state count drift");
            }
        }
        if (values.size() != 109) {
            throw new ExceptionInInitializerError("POST-EV-8 exact-state closure drift");
        }
        return Map.copyOf(values);
    }

    private static Map<String, Ev9Authority> loadEv9Authorities() {
        Map<String, Integer> familyCounts = new LinkedHashMap<>();
        Map<String, Ev9Authority> values = new LinkedHashMap<>();
        int nextOrdinal = 0;
        for (String line : Mc263PostSupportClosureData.rows()) {
            String[] row = line.split("\\t", -1);
            if (row[0].equals("E9H")) {
                if (row.length != 4 || familyCounts.put(row[1], Integer.parseInt(row[3])) != null) {
                    throw new ExceptionInInitializerError("malformed POST-EV-9 family row");
                }
            } else if (row[0].equals("E9S")) {
                Ev9Authority authority = new Ev9Authority(row);
                if (authority.ordinal() != nextOrdinal++
                        || !familyCounts.containsKey(authority.family())
                        || values.put(row[3], authority) != null) {
                    throw new ExceptionInInitializerError("malformed POST-EV-9 subject order");
                }
            }
        }
        if (familyCounts.size() != Mc263PostSupportClosureData.EV9_FAMILY_ROW_COUNT
                || values.size() != Mc263PostSupportClosureData.EV9_SUBJECT_ROW_COUNT) {
            throw new ExceptionInInitializerError("POST-EV-9 subject cardinality drift");
        }
        for (Map.Entry<String, Integer> family : familyCounts.entrySet()) {
            long observed = values.values().stream()
                    .filter(authority -> authority.family().equals(family.getKey())).count();
            if (observed != family.getValue()) {
                throw new ExceptionInInitializerError("POST-EV-9 family cardinality drift");
            }
        }
        return Map.copyOf(values);
    }

    private static Map<String, String> loadEv9Support() {
        Map<String, String> values = new LinkedHashMap<>();
        for (String line : Mc263PostSupportClosureData.rows()) {
            String[] row = line.split("\\t", -1);
            if (!row[0].equals("E9C")) continue;
            if (row.length != 3
                    || row[2].length() != Mc263PostSupportClosureData.EV9_SUBJECT_ROW_COUNT
                    || values.put(row[1], row[2]) != null) {
                throw new ExceptionInInitializerError("malformed POST-EV-9 support row");
            }
        }
        if (values.size() != Mc263PostSupportClosureData.EV9_SUPPORT_ROW_COUNT) {
            throw new ExceptionInInitializerError("POST-EV-9 support cardinality drift");
        }
        return Map.copyOf(values);
    }

    private static Map<String, Ev10Authority> loadEv10Authorities() {
        Map<String, Integer> familyStates = new LinkedHashMap<>();
        Map<String, Ev10Authority> values = new LinkedHashMap<>();
        int pairs = 0;
        for (String line : Mc263PostSupportClosureData.rows()) {
            String[] row = line.split("\\t", -1);
            if (row[0].equals("E10H")) {
                if (row.length != 5 || familyStates.put(row[1], Integer.parseInt(row[4])) != null) {
                    throw new ExceptionInInitializerError("malformed POST-EV-10 family row");
                }
            } else if (row[0].equals("E10P")) {
                Ev10Authority authority = new Ev10Authority(row);
                if (!familyStates.containsKey(authority.blockKey())
                        || values.put(authority.dryState(), authority) != null
                        || values.put(authority.waterloggedState(), authority) != null) {
                    throw new ExceptionInInitializerError(
                            "malformed or duplicate POST-EV-10 paired state");
                }
                pairs++;
            }
        }
        if (familyStates.size() != Mc263PostSupportClosureData.EV10_FAMILY_ROW_COUNT
                || pairs != Mc263PostSupportClosureData.EV10_PAIR_ROW_COUNT
                || values.size() != pairs * 2) {
            throw new ExceptionInInitializerError("POST-EV-10 closure cardinality drift");
        }
        return Map.copyOf(values);
    }

    /** Official per-candidate POST-EV-7 predicate for one exact carrier state. */
    public static boolean ev7Fact(Mc263FeatureBlockState state, String name) {
        String bits = EV7_FACTS.get(
                Objects.requireNonNull(state, "POST-EV-7 fact subject").exactState());
        if (bits == null) {
            throw new IllegalStateException("no authenticated POST-EV-7 fact authority for "
                    + state.exactState());
        }
        for (int index = 0; index < Mc263PostSupportClosureData.FACT_NAMES.length; index++) {
            if (Mc263PostSupportClosureData.FACT_NAMES[index].equals(name)) {
                return bits.charAt(index) == '1';
            }
        }
        throw new IllegalStateException("no authenticated POST-EV-7 fact named " + name);
    }

    /** Published POST-EV-7 table verdict, or {@code null} when the receipt has no such row. */
    public static String ev7Table(String... key) {
        return EV7_TABLES.get(String.join("\t", key));
    }

    /** Official POST-EV-7 family for one block key, or {@code null} when it has no lane. */
    public static Ev7Family ev7Family(String blockKey) {
        return EV7_FAMILIES.get(Objects.requireNonNull(blockKey, "POST-EV-7 family block"));
    }

    /** Official POST-EV-8 family for one block key, or {@code null} outside the final lane. */
    public static Ev8Family ev8Family(Mc263FeatureBlockState state) {
        return EV8_STATE_FAMILIES.get(
                Objects.requireNonNull(state, "POST-EV-8 family state").exactState());
    }

    /** Total POST-EV-9 authority for an attached subject, or {@code null} outside the lane. */
    public static Ev9Authority ev9Authority(Mc263FeatureBlockState state) {
        return EV9_AUTHORITIES.get(
                Objects.requireNonNull(state, "POST-EV-9 subject").exactState());
    }

    /** Official paired waterlogged-state decorator, or {@code null} outside POST-EV-10. */
    public static Ev10Authority ev10Authority(Mc263FeatureBlockState state) {
        return EV10_AUTHORITIES.get(
                Objects.requireNonNull(state, "POST-EV-10 subject").exactState());
    }

    /** Official attached-family authority, or {@code null} outside every published generation. */
    public static AttachmentAuthority attachmentAuthority(Mc263FeatureBlockState state) {
        return ATTACHMENT_AUTHORITIES.get(
                Objects.requireNonNull(state, "attachment subject").exactState());
    }

    /** Official support verdict for one exact candidate and POST-EV-9 subject ordinal. */
    public static boolean ev9Supports(Mc263FeatureBlockState candidate, int subjectOrdinal) {
        String bits = EV9_SUPPORT.get(
                Objects.requireNonNull(candidate, "POST-EV-9 support candidate").exactState());
        if (bits == null) {
            bits = connectionShapeClosure(EV9_SHAPE_SUPPORT, candidate.exactState());
        }
        if (bits == null) {
            throw new IllegalStateException("no authenticated POST-EV-9 support authority for "
                    + candidate.exactState());
        }
        if (subjectOrdinal < 0 || subjectOrdinal >= bits.length()) {
            throw new IllegalArgumentException("POST-EV-9 subject ordinal out of range");
        }
        return bits.charAt(subjectOrdinal) == '1';
    }

    /** Exact published POST-EV-8 step; an unobserved live combination fails closed. */
    public static Ev8Verdict ev8Verdict(String family, String exactState, String scenario,
            Direction direction) {
        String key = String.join("\\t", family, exactState, scenario,
                Objects.requireNonNull(direction, "POST-EV-8 direction").name()
                        .toLowerCase(java.util.Locale.ROOT));
        Ev8Verdict verdict = EV8_VERDICTS.get(key);
        if (verdict == null) {
            throw new IllegalStateException("no authenticated POST-EV-8 verdict for " + key);
        }
        return verdict;
    }

    /** Authenticated support closure for one exact state, or {@code null} when it has none. */
    public static SupportAuthority supportAuthority(Mc263FeatureBlockState state) {
        return SUPPORT_AUTHORITIES.get(
                Objects.requireNonNull(state, "POST support subject").exactState());
    }

    private static Map<String, String> loadSupportMasks() {
        Map<String, String> values = new LinkedHashMap<>();
        for (String line : Mc263PostSupportClosureData.rows()) {
            String[] row = line.split("\\t", -1);
            if (!row[0].equals("S")) continue;
            if (row.length != 3
                    || row[2].length() != Mc263PostSupportClosureData.SUPPORT_CLASS_NAMES.length
                    || values.put(row[1], row[2]) != null) {
                throw new ExceptionInInitializerError("malformed POST support closure row");
            }
        }
        if (values.size() != Mc263PostSupportClosureData.SUPPORT_ROW_COUNT) {
            throw new ExceptionInInitializerError("POST support closure cardinality drift");
        }
        if (!POST_EVIDENCE_SHA256.equals(Mc263PostSupportClosureData.RECEIPT_SHA256)) {
            throw new ExceptionInInitializerError("POST support closure receipt identity drift");
        }
        return Map.copyOf(values);
    }

    /**
     * Official POST-EV-6 support-class ordinal for one published class name.
     *
     * <p>The names are the {@code POST_SUPPORT_CLASS_EV6} rows of the pinned transcript, so an
     * unpublished name has no ordinal and the caller fails closed instead of guessing one.</p>
     */
    public static int supportClassOrdinal(String supportClass) {
        Objects.requireNonNull(supportClass, "POST support class");
        for (int index = 0;
                index < Mc263PostSupportClosureData.SUPPORT_CLASS_NAMES.length; index++) {
            if (Mc263PostSupportClosureData.SUPPORT_CLASS_NAMES[index].equals(supportClass)) {
                return index;
            }
        }
        throw new IllegalStateException("no authenticated POST support class " + supportClass);
    }

    /**
     * Official {@code canSurvive} verdict of one POST-EV-6 support class over one exact DOWN
     * candidate.
     *
     * <p>The published {@code POST_SUPPORT_EV6} rows answer directly. A catalog state the
     * transcript never published as a candidate is answered by the connection-shape closure of
     * its own family, exactly as the neighbour and POST-EV-9 closures already are; a column whose
     * published rows ever disagree under one shape descriptor is dropped from that closure, so it
     * keeps failing closed rather than borrowing another shape's verdict.</p>
     */
    public static boolean supportClassSurvives(Mc263FeatureBlockState below, int supportClass) {
        String exact = Objects.requireNonNull(below, "POST support neighbour").exactState();
        if (supportClass < 0
                || supportClass >= Mc263PostSupportClosureData.SUPPORT_CLASS_NAMES.length) {
            throw new IllegalArgumentException("POST support class ordinal out of range");
        }
        String bits = SUPPORT_MASKS.get(exact);
        if (bits == null) bits = connectionShapeClosure(SUPPORT_SHAPE_MASKS, exact);
        if (bits == null || bits.charAt(supportClass) == AMBIGUOUS_SUPPORT_BIT) {
            throw new IllegalStateException("no authenticated POST support authority for " + exact);
        }
        return bits.charAt(supportClass) == '1';
    }

    /** Marks one support column the connection-shape closure may not answer. */
    private static final char AMBIGUOUS_SUPPORT_BIT = '?';

    /**
     * Connection-shape closure over the authenticated POST-EV-6 support vectors, column by
     * column. The stairs, wall, fence and pane families publish most support columns as a
     * function of the connection shape alone; the columns that do not agree are marked ambiguous
     * instead of being resolved, so only the proven ones ever answer.
     */
    private static Map<String, String> loadSupportShapeMasks() {
        int columns = Mc263PostSupportClosureData.SUPPORT_CLASS_NAMES.length;
        Map<String, char[]> published = new LinkedHashMap<>();
        for (Map.Entry<String, String> row : SUPPORT_MASKS.entrySet()) {
            String descriptor = connectionShapeDescriptor(row.getKey());
            if (descriptor == null) continue;
            char[] merged = published.get(descriptor);
            if (merged == null) {
                published.put(descriptor, row.getValue().toCharArray());
                continue;
            }
            for (int index = 0; index < columns; index++) {
                if (merged[index] != row.getValue().charAt(index)) {
                    merged[index] = AMBIGUOUS_SUPPORT_BIT;
                }
            }
        }
        Map<String, String> values = new LinkedHashMap<>();
        published.forEach((descriptor, bits) -> values.put(descriptor, new String(bits)));
        return Map.copyOf(values);
    }

    private static Map<String, SupportAuthority> loadSupportAuthorities() {
        Map<String, SupportAuthority> values = new LinkedHashMap<>();
        for (String line : Mc263PostSupportClosureData.rows()) {
            String[] row = line.split("\\t", -1);
            int expected = switch (row[0]) {
                case "P" -> 6; case "Q" -> 9; case "D" -> 12; default -> -1;
            };
            if (expected < 0) continue;
            if (row.length != expected
                    || values.put(row[1], new SupportAuthority(row)) != null) {
                throw new ExceptionInInitializerError("malformed POST plant closure row");
            }
        }
        if (values.size() != Mc263PostSupportClosureData.PLANT_ROW_COUNT) {
            throw new ExceptionInInitializerError("POST plant closure cardinality drift");
        }
        return Map.copyOf(values);
    }

    public static NeighborAuthority requireNeighborAuthority(Mc263FeatureBlockState state) {
        Objects.requireNonNull(state, "POST neighbor state");
        NeighborAuthority authority = NEIGHBOR_AUTHORITIES.get(state.exactState());
        if (authority == null) {
            authority = connectionShapeClosure(NEIGHBOR_SHAPE_CLOSURE, state.exactState());
        }
        if (authority == null) {
            throw new IllegalStateException("no authenticated POST neighbor registry authority for "
                    + state.exactState());
        }
        return authority;
    }

    private static Map<String, NeighborAuthority> loadNeighborAuthorities() {
        Map<String, NeighborAuthority> values = new LinkedHashMap<>();
        for (Map.Entry<String, String[]> row : NEIGHBOR_ROWS.entrySet()) {
            values.put(row.getKey(), new NeighborAuthority(row.getValue()));
        }
        return Map.copyOf(values);
    }

    private static Map<String, String[]> loadNeighborRows() {
        byte[] compressed = Base64.getDecoder().decode(NEIGHBOR_TABLE_GZIP_BASE64);
        if (!sha256(compressed).equals(NEIGHBOR_TABLE_GZIP_SHA256)) {
            throw new ExceptionInInitializerError("POST neighbor table identity drift");
        }
        Map<String, String[]> values = new LinkedHashMap<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new GZIPInputStream(new ByteArrayInputStream(compressed)),
                StandardCharsets.US_ASCII))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] row = line.split("\\t", -1);
                if (row.length != 16 || !row[0].equals("POST_NEIGHBOR")
                        || !row[1].equals("W")) {
                    throw new IllegalStateException("malformed POST neighbor authority row");
                }
                if (values.put(row[2], row) != null) {
                    throw new IllegalStateException("duplicate POST neighbor authority: " + row[2]);
                }
            }
        } catch (IOException invalid) {
            throw new ExceptionInInitializerError(invalid);
        }
        if (values.size() != NEIGHBOR_TABLE_ROWS) {
            throw new ExceptionInInitializerError("POST neighbor table cardinality drift");
        }
        if (!POST_EVIDENCE_SHA256.equals(
                Mc263ExactStatePostNeighborClosureData.SOURCE_SHA256)) {
            throw new ExceptionInInitializerError("POST supplement source identity drift");
        }
        for (String line : Mc263ExactStatePostNeighborClosureData.rows()) {
            String[] row = line.split("\\t", -1);
            if (row.length != 16 || !row[0].equals("POST_NEIGHBOR")
                    || !row[1].equals("W")
                    || values.put(row[2], row) != null) {
                throw new ExceptionInInitializerError(
                        "malformed or duplicate POST supplement authority");
            }
        }
        if (values.size() != NEIGHBOR_TABLE_ROWS
                + Mc263ExactStatePostNeighborClosureData.AUTHENTICATED_ROW_COUNT) {
            throw new ExceptionInInitializerError("POST supplement cardinality drift");
        }
        int appended = 0;
        for (String line : Mc263PostSupportClosureData.rows()) {
            String[] row = line.split("\\t", -1);
            if (!row[0].equals("N")) continue;
            String[] neighbor = new String[row.length + 1];
            neighbor[0] = "POST_NEIGHBOR";
            System.arraycopy(row, 1, neighbor, 2, row.length - 1);
            neighbor[1] = "W";
            if (neighbor.length != 16) {
                throw new ExceptionInInitializerError(
                        "malformed POST appended neighbor authority");
            }
            // Rows already carried by the pinned table or the closure supplement repeat the same
            // official facts; the appended transcript only has to close the remaining states.
            values.putIfAbsent(neighbor[2], neighbor);
            appended++;
        }
        if (appended != Mc263PostSupportClosureData.NEIGHBOR_ROW_COUNT) {
            throw new ExceptionInInitializerError("POST appended neighbor cardinality drift");
        }
        return Map.copyOf(values);
    }

    /**
     * Connection-shape closure over the authenticated POST neighbor rows.
     *
     * <p>The official transcript publishes the stairs, wall, fence and pane neighbor rows as a
     * function of the connection shape alone: every authenticated row sharing one shape
     * descriptor carries byte-identical official facts, across every block of the family. The
     * closure is rebuilt from the authenticated rows on each load and fails closed the moment two
     * published rows under one descriptor ever disagree, so a state appended to the exact catalog
     * after the transcript was published is answered by the official row of its own shape rather
     * than by an invented one.</p>
     */
    private static Map<String, NeighborAuthority> loadNeighborShapeClosure() {
        Map<String, String> signatures = new LinkedHashMap<>();
        Map<String, NeighborAuthority> values = new LinkedHashMap<>();
        for (Map.Entry<String, String[]> row : NEIGHBOR_ROWS.entrySet()) {
            String descriptor = connectionShapeDescriptor(row.getKey());
            if (descriptor == null) continue;
            String[] fields = row.getValue();
            String signature = String.join("\t", Arrays.copyOfRange(fields, 3, fields.length));
            String published = signatures.putIfAbsent(descriptor, signature);
            if (published == null) {
                values.put(descriptor, new NeighborAuthority(fields));
            } else if (!published.equals(signature)) {
                throw new ExceptionInInitializerError(
                        "POST connection-shape neighbor closure drift for " + descriptor);
            }
        }
        return Map.copyOf(values);
    }

    /** Connection-shape closure over the authenticated POST-EV-9 support vectors. */
    private static Map<String, String> loadEv9ShapeSupport() {
        Map<String, String> values = new LinkedHashMap<>();
        for (Map.Entry<String, String> support : EV9_SUPPORT.entrySet()) {
            String descriptor = connectionShapeDescriptor(support.getKey());
            if (descriptor == null) continue;
            String published = values.putIfAbsent(descriptor, support.getValue());
            if (published != null && !published.equals(support.getValue())) {
                throw new ExceptionInInitializerError(
                        "POST-EV-9 connection-shape support closure drift for " + descriptor);
            }
        }
        return Map.copyOf(values);
    }

    /**
     * Connection-shape closure answers and the published rows they stand in for, exposed so the
     * guard test can prove the fallback agrees with the transcript on every published state of
     * the four connection-shape families instead of only failing closed when it disagrees.
     */
    static NeighborAuthority shapeClosureNeighborAuthority(String exactState) {
        return connectionShapeClosure(NEIGHBOR_SHAPE_CLOSURE, exactState);
    }

    static NeighborAuthority publishedNeighborAuthority(String exactState) {
        return NEIGHBOR_AUTHORITIES.get(exactState);
    }

    static String shapeClosureSupportMask(String exactState) {
        return connectionShapeClosure(SUPPORT_SHAPE_MASKS, exactState);
    }

    static String publishedSupportMask(String exactState) {
        return SUPPORT_MASKS.get(exactState);
    }

    static String shapeClosureEv9Support(String exactState) {
        return connectionShapeClosure(EV9_SHAPE_SUPPORT, exactState);
    }

    static String publishedEv9Support(String exactState) {
        return EV9_SUPPORT.get(exactState);
    }

    static String neighborAuthoritySignature(NeighborAuthority authority) {
        return authority == null ? null : authority.signature();
    }

    private static <T> T connectionShapeClosure(Map<String, T> closure, String exactState) {
        String descriptor = connectionShapeDescriptor(exactState);
        return descriptor == null ? null : closure.get(descriptor);
    }

    /**
     * Official connection-shape descriptor of one exact state, or {@code null} when the state is
     * outside the four families whose published POST authorities depend on the connection shape
     * alone. Any unpublished property name or value leaves the state without a descriptor, so it
     * still fails closed instead of borrowing another shape's row.
     */
    private static String connectionShapeDescriptor(String exactState) {
        int bracket = exactState.indexOf('[');
        if (bracket < 0 || !exactState.endsWith("]")) return null;
        String block = exactState.substring(0, bracket);
        Map<String, String> properties = new LinkedHashMap<>();
        for (String property : exactState.substring(bracket + 1, exactState.length() - 1)
                .split(",", -1)) {
            int assign = property.indexOf('=');
            if (assign < 0 || properties.put(property.substring(0, assign),
                    property.substring(assign + 1)) != null) {
                return null;
            }
        }
        if (block.endsWith("_stairs")) {
            String facing = properties.get("facing");
            String half = properties.get("half");
            String shape = properties.get("shape");
            String waterlogged = properties.get("waterlogged");
            if (properties.size() != 4 || facing == null || half == null || shape == null
                    || waterlogged == null) {
                return null;
            }
            return "STAIRS|" + facing + "|" + half + "|" + shape + "|" + waterlogged;
        }
        if (block.endsWith("_wall")) {
            if (properties.size() != 6 || properties.get("up") == null
                    || properties.get("waterlogged") == null) {
                return null;
            }
            int sides = connectionMask(properties, "none", "low", "tall");
            return sides < 0 ? null : "WALL|" + sides;
        }
        if (block.endsWith("_fence") || block.endsWith("_pane")) {
            if (properties.size() != 5 || properties.get("waterlogged") == null) return null;
            int sides = connectionMask(properties, "false", "true");
            if (sides < 0) return null;
            return (block.endsWith("_fence") ? "FENCE|" : "PANE|") + sides;
        }
        return null;
    }

    /**
     * Bit mask of the connected horizontal sides, or {@code -1} when a side carries a value the
     * official transcript never published for the family.
     */
    private static int connectionMask(Map<String, String> properties, String detached,
            String... attached) {
        String[] sides = {"north", "east", "south", "west"};
        int mask = 0;
        for (int index = 0; index < sides.length; index++) {
            String value = properties.get(sides[index]);
            if (value == null) return -1;
            if (value.equals(detached)) continue;
            if (!Arrays.asList(attached).contains(value)) return -1;
            mask |= 1 << index;
        }
        return mask;
    }

    private static String sha256(byte[] bytes) {
        try {
            return java.util.HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new ExceptionInInitializerError(impossible);
        }
    }

    public enum Direction {
        WEST(-1, 0, 0), EAST(1, 0, 0), NORTH(0, 0, -1), SOUTH(0, 0, 1),
        DOWN(0, -1, 0), UP(0, 1, 0);

        final int dx, dy, dz;
        Direction(int dx, int dy, int dz) { this.dx = dx; this.dy = dy; this.dz = dz; }
    }

    public record Position(int x, int y, int z) {
        Position relative(Direction direction) {
            return new Position(x + direction.dx, y + direction.dy, z + direction.dz);
        }
    }

    public interface MutableRandom { int nextInt(int bound); }

    public interface MutableWorld {
        Mc263FeatureBlockState blockState(Position position);
        void setBlock(Position position, String exactState, int flags);
        void scheduleBlockTick(Position position, String blockKey, int delay, int priority);
        void scheduleFluidTick(Position position, String fluidKey, int delay, int priority);
    }

    /** Exact authority behavior calls made by the official finalization loop. */
    public interface ActivationContext {
        /**
         * Binds one activation to the live canonical FEATURES carrier. Stateless test contexts
         * may retain themselves; production contexts return a run-scoped bound instance.
         */
        default ActivationContext bind(Mc263FeaturesRegion region) {
            Objects.requireNonNull(region, "POST region");
            return this;
        }
        long gameTime();
        MutableRandom random();
        Mc263FeatureBlockState readFull(Position position);
        ForeignMutationSink foreignSink();
        void tickContainedFluid(MutableWorld world, Position position,
                Mc263FeatureBlockState originalState);
        void tickLiquidBlock(MutableWorld world, Position position,
                Mc263FeatureBlockState originalState, MutableRandom random);
        Mc263FeatureBlockState updateShape(MutableWorld world, Position position,
                Mc263FeatureBlockState currentState, Direction direction,
                Position neighborPosition, Mc263FeatureBlockState neighborState,
                MutableRandom random);
    }

    /** Commit target for radius-one FULL cells that do not belong to the finalized chunk. */
    public interface ForeignMutationSink {
        /** Atomically accepts the complete validated foreign batch or throws without mutation. */
        void commit(List<ForeignMutation> mutations);
    }

    public sealed interface ForeignMutation permits ForeignState, ForeignBlockTick,
            ForeignFluidTick {}
    public record ForeignState(Position position, Mc263FeatureBlockState state, int flags)
            implements ForeignMutation {}
    public record ForeignBlockTick(Position position, String blockKey, int delay, int priority,
                                   long subTickOrder) implements ForeignMutation {}
    public record ForeignFluidTick(Position position, String fluidKey, int delay, int priority,
                                   long subTickOrder) implements ForeignMutation {}

    public record Result(int processedOccurrences, int centerWrites, int foreignWrites,
                         int blockTicks, int fluidTicks, List<String> trace) {
        public Result { trace = List.copyOf(trace); }
    }

    public static Result resolve(Mc263FeaturesRegion region, ActivationContext context) {
        Objects.requireNonNull(region, "region");
        context = Objects.requireNonNull(
                Objects.requireNonNull(context, "context").bind(region),
                "bound POST activation context");
        MutableRandom random = Objects.requireNonNull(context.random(), "authority random");
        return resolveBound(region, context, random);
    }

    /** Resolves POST against the caller's exact mutable WorldgenRandom stream. */
    public static Result resolve(Mc263FeaturesRegion region, ActivationContext context,
            MutableRandom random) {
        Objects.requireNonNull(region, "region");
        context = Objects.requireNonNull(
                Objects.requireNonNull(context, "context").bind(region),
                "bound POST activation context");
        return resolveBound(region, context,
                Objects.requireNonNull(random, "caller WorldgenRandom"));
    }

    private static Result resolveBound(Mc263FeaturesRegion region, ActivationContext context,
            MutableRandom random) {
        Objects.requireNonNull(context.foreignSink(), "foreign mutation sink");
        if (!region.postprocessCommitReady()) {
            throw new IllegalStateException("POST cannot run while a FEATURES source is active");
        }
        // Reading game time is deliberately part of activation preflight even though scheduled
        // ticks are retained as exact relative delays in the immutable carrier.
        context.gameTime();

        Mc263FeaturesRegion.CenterSnapshot before = region.snapshotCenter();
        Journal journal = new Journal(region, context, before);
        int processed = 0;
        for (List<Mc263FeaturesRegion.PostprocessMark> section
                : before.postprocessMarksBySection()) {
            for (Mc263FeaturesRegion.PostprocessMark mark : section) {
                Position position = new Position(before.chunkX() * Blocks.CHUNK_X + mark.localX(),
                        mark.blockY(), before.chunkZ() * Blocks.CHUNK_Z + mark.localZ());
                journal.trace.add("mark:" + position.x + "," + position.y + "," + position.z);
                Mc263FeatureBlockState original = journal.blockState(position);
                if (original.fluidKind() != Mc263FeatureBlockState.FluidKind.NONE) {
                    journal.trace.add("fluid:" + original.fluidTypeKey());
                    context.tickContainedFluid(journal, position, original);
                }
                if (isLiquidBlock(original)) {
                    journal.trace.add("liquid:" + original.blockKey());
                    context.tickLiquidBlock(journal, position, original, random);
                    processed++;
                    continue;
                }
                Mc263FeatureBlockState updated = original;
                for (Direction direction : Direction.values()) {
                    Position neighborPosition = position.relative(direction);
                    Mc263FeatureBlockState neighbor = journal.blockState(neighborPosition);
                    journal.trace.add("shape:" + direction.name());
                    updated = Objects.requireNonNull(context.updateShape(journal, position,
                            updated, direction, neighborPosition, neighbor, random),
                            "updateShape output");
                    // Re-entering the exact catalog is mandatory even for authority-created
                    // instances and makes an unsupported bubble/lava/property result fail closed.
                    updated = Mc263FeatureBlockState.fromExact(updated.exactState());
                }
                journal.setBlock(position, updated.exactState(), UPDATE_FLAGS);
                processed++;
            }
        }
        journal.commit();
        region.clearPostprocessMarks();
        return new Result(processed, journal.centerWrites, journal.foreignWrites,
                journal.blockTicks.size(), journal.fluidTicks.size(), journal.trace);
    }

    private static boolean isLiquidBlock(Mc263FeatureBlockState state) {
        return state.blockKey().equals("minecraft:water")
                || state.blockKey().equals("minecraft:lava");
    }

    private record TickIdentity(Position position, String key) {}
    private record StateWrite(Mc263FeatureBlockState state, int flags) {}
    private record BlockTick(Position position, String key, int delay, int priority,
                             long order) {}
    private record FluidTick(Position position, String key, int delay, int priority,
                             long order) {}

    private static final class Journal implements MutableWorld {
        private final Mc263FeaturesRegion region;
        private final ActivationContext context;
        private final int centerChunkX, centerChunkZ;
        private final List<Map.Entry<Position, StateWrite>> writes = new ArrayList<>();
        private final Map<Position, StateWrite> latestWrites = new LinkedHashMap<>();
        private final List<BlockTick> blockTicks = new ArrayList<>();
        private final List<FluidTick> fluidTicks = new ArrayList<>();
        private final Set<TickIdentity> blockTickKeys = new LinkedHashSet<>();
        private final Set<TickIdentity> fluidTickKeys = new LinkedHashSet<>();
        private final List<String> trace = new ArrayList<>();
        private long nextBlockOrder;
        private long nextFluidOrder;
        private int centerWrites;
        private int foreignWrites;

        Journal(Mc263FeaturesRegion region, ActivationContext context,
                Mc263FeaturesRegion.CenterSnapshot before) {
            this.region = region;
            this.context = context;
            centerChunkX = before.chunkX(); centerChunkZ = before.chunkZ();
            int index = 0;
            for (Mc263FeaturesRegion.ScheduledBlockTick tick : before.scheduledBlockTicks()) {
                Position p = local(tick.localX(), tick.blockY(), tick.localZ());
                blockTickKeys.add(new TickIdentity(p, tick.blockKey()));
                nextBlockOrder = Math.max(nextBlockOrder,
                        Math.max(tick.subTickOrder() + 1, (long) ++index));
            }
            index = 0;
            for (Mc263FeaturesRegion.ScheduledFluidTick tick : before.scheduledFluidTicks()) {
                Position p = local(tick.localX(), tick.blockY(), tick.localZ());
                fluidTickKeys.add(new TickIdentity(p, tick.fluidKey()));
                nextFluidOrder = Math.max(nextFluidOrder,
                        Math.max(tick.subTickOrder() + 1, (long) ++index));
            }
        }

        private Position local(int x, int y, int z) {
            return new Position(centerChunkX * 16 + x, y, centerChunkZ * 16 + z);
        }

        private boolean center(Position p) {
            return Math.floorDiv(p.x, 16) == centerChunkX
                    && Math.floorDiv(p.z, 16) == centerChunkZ;
        }

        @Override public Mc263FeatureBlockState blockState(Position position) {
            Objects.requireNonNull(position, "position");
            StateWrite staged = latestWrites.get(position);
            if (staged != null) return staged.state;
            if (center(position)) return region.blockState(position.x, position.y, position.z);
            int dx = Math.abs(Math.floorDiv(position.x, 16) - centerChunkX);
            int dz = Math.abs(Math.floorDiv(position.z, 16) - centerChunkZ);
            if (dx > 1 || dz > 1) {
                throw new IllegalArgumentException("POST read escaped radius-one FULL view: "
                        + position);
            }
            return Objects.requireNonNull(context.readFull(position), "FULL live state");
        }

        @Override public void setBlock(Position position, String exactState, int flags) {
            if (position.y < Blocks.MIN_Y || position.y > Blocks.MAX_Y) {
                throw new IllegalArgumentException("POST write lies outside build height: "
                        + position);
            }
            Mc263FeatureBlockState state = Mc263FeatureBlockState.fromExact(exactState);
            StateWrite write = new StateWrite(state, flags);
            writes.add(Map.entry(position, write));
            latestWrites.put(position, write);
            trace.add("set:" + position.x + "," + position.y + "," + position.z + ":"
                    + state.exactState() + ":" + flags);
        }

        @Override public void scheduleBlockTick(Position position, String blockKey, int delay,
                int priority) {
            Mc263FeatureBlockState state = Mc263FeatureBlockState.forSemanticBlockKey(blockKey);
            if (delay < 0) throw new IllegalArgumentException("negative block tick delay");
            Mc263FinalChunkSidecars.TickPriority.fromValue(priority);
            TickIdentity identity = new TickIdentity(position, state.blockKey());
            if (!blockTickKeys.add(identity)) return;
            blockTicks.add(new BlockTick(position, state.blockKey(), delay, priority,
                    nextBlockOrder++));
            trace.add("btick:" + state.blockKey() + ":" + delay + ":" + priority);
        }

        @Override public void scheduleFluidTick(Position position, String fluidKey, int delay,
                int priority) {
            String key = Mc263FeatureBlockState.requireFluidTickKey(fluidKey);
            if (delay < 0) throw new IllegalArgumentException("negative fluid tick delay");
            Mc263FinalChunkSidecars.TickPriority.fromValue(priority);
            TickIdentity identity = new TickIdentity(position, key);
            if (!fluidTickKeys.add(identity)) return;
            fluidTicks.add(new FluidTick(position, key, delay, priority, nextFluidOrder++));
            trace.add("ftick:" + key + ":" + delay + ":" + priority);
        }

        void commit() {
            List<ForeignMutation> foreign = new ArrayList<>();
            for (Map.Entry<Position, StateWrite> entry : writes) {
                if (!center(entry.getKey())) foreign.add(new ForeignState(entry.getKey(),
                        entry.getValue().state, entry.getValue().flags));
            }
            for (BlockTick tick : blockTicks) if (!center(tick.position)) {
                foreign.add(new ForeignBlockTick(tick.position, tick.key, tick.delay,
                        tick.priority, tick.order));
            }
            for (FluidTick tick : fluidTicks) if (!center(tick.position)) {
                foreign.add(new ForeignFluidTick(tick.position, tick.key, tick.delay,
                        tick.priority, tick.order));
            }
            // Foreign acceptance happens as one atomic operation before the infallible,
            // prevalidated center replay. A rejecting sink therefore leaves the region untouched.
            context.foreignSink().commit(List.copyOf(foreign));
            for (Map.Entry<Position, StateWrite> entry : writes) {
                Position p = entry.getKey(); StateWrite write = entry.getValue();
                if (center(p)) {
                    region.applyPostprocessState(p.x, p.y, p.z, write.state);
                    centerWrites++;
                } else {
                    foreignWrites++;
                }
            }
            for (BlockTick tick : blockTicks) {
                if (center(tick.position)) region.applyPostprocessBlockTick(tick.position.x,
                        tick.position.y, tick.position.z, tick.key, tick.delay, tick.priority,
                        tick.order);
            }
            for (FluidTick tick : fluidTicks) {
                if (center(tick.position)) region.applyPostprocessFluidTick(tick.position.x,
                        tick.position.y, tick.position.z, tick.key, tick.delay, tick.priority,
                        tick.order);
            }
        }
    }
}
