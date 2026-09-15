package com.gameexpert.terrain.mc.feature;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.zip.GZIPInputStream;

/** Generated authenticated exact-state tranche. */
final class Mc263ExactStateProductionClosurePostData {
    static final int AUTHENTICATED_STATE_COUNT = 1812;
    static final int AUTHENTICATED_BLOCK_KEY_COUNT = 44;
    static final int RECEIPT_BASE_STATE_COUNT = 3_203;
    static final String RECEIPT_SHA256 = "8da716044ae5b5321119b834ad7cb36fc1414f62cb31886a3d27cde34cb4cfe9";
    private static final String GZIP_BASE64 = "H4sIAAAAAAAC/7Wd0W4byRFF3/Mt+wUB/CWLgKAkWmKWJgWShnfz9ZHFXViQxGFVnXPfnGB96nZ1dc2w+/b423a/uT+uv57/vb5f"
            + "32/Xq6+b/f3m9836dP5yPn7f/LY/HM9Plz+eDt//+eOP9Xlz3B0eHzcPX76ud6eX/2fz99/4z7++cebl/1hAns7r7fH0+9eX/7F/"
            + "/PIT/dvTevf1y/nw/Nvpaf28+bLd7zfH1W7z9fxRrQmEAo/bxydX4UfiWOLL5Lg5vAKEAs0cXiOOJZ7Ox7Wo71NcSdzrqlNXSYdI"
            + "JU7nuIWci0QrpUOkEtU8osXyKRGsljqvJO/1yaQulw6RSpxOcws5F4mWS4dIJap5RMvlUyJYLnVeSd7PlzV1tTSAUOB0jjvEsUS0"
            + "UhpAKNDMIVomnwHBKinj3ou7W3+7Oxze/vS5/E66/Pa5/Ln/gwpQr/+kWoa++Zn2tzBD6iJVkLqU1A8zr1A9qb+GLyrt5PTNj/S3"
            + "JQWnv0wVpMLpH1A9qa3p70NbQquLv6fUaylV6DSnWpvq76WNhWpVqvUovOl3tz3eP9n9ecpsyDT6yJQ5kzlemlMmltlbQ5xpyVxa"
            + "QRj5QeRxe//H1d3Fu8P5fPjW/NHGkVhk4UeHwAQyqz/eOBKLdHNZ/Qm3jOzvJEAek4dT2N9GqPKcQmxvIvRwYv5I/b3ZR9YaY5PJ"
            + "ZQ5z2YUSoaQmm0wuU86nVJ9OgyTHeE0eT6PTI9EZHgVCgWYOSR2+OUDQ+mSTyWUOk9mFEqGkLJtMLlPOp1SfTp8k57dNHk+j0yfR"
            + "4S0FQoFmDkkd/jpC0tpkD4lFDjPZZAKZpCJ7SCzSzaVTl057BAf2PRxOodMbyWk95DF5Yv569fdjvdtdNjR3hx9/72f+/NNlO/Pn"
            + "n74/L287v/w3GvbXbumcurRRnKGeXwAWNpKBN5vQA6k//04gsRWsKheltkCti32F+UXbw6pyB7ltUUdi/5kzp311wbJkluEKFwm2"
            + "C9jtDhWwk2BQw6+IyRPYBTuCQT1MsarccjUMqSOxSt9pkiXJdj0Y/aEHZhXh9oePz06rJCpkSbJTEu5LTw88KQn1tedzrvHe0yXD"
            + "VLhtQn6PaJJTqVgqi8OP/evWwH7zsHrcrU+n1fN6f8OO3nSP+jE+7kg4IZZsXHoIZxA9x5weIjQIfSJa9rpqgLmhWg8RGoQyD1PL"
            + "6CRAplanPs0l/uReRnMSBpc0nAhiUzJc10qAeI6EQgo1JMNSrgQw5iDSjQw3uxwgMgB5Ahpr4P5pczz+ZV/BBNSp1OkKAlRBaquU"
            + "FaondaFoDehtocYVTEAVpMLpFx7ic2hr+vnDepE5fkArVE/qNKdamxIeuAZUEyoktFOjh7u73ctT+mGzeT7tXv6Ke5vMxaviC3YZ"
            + "mT+X33dyiWxPtpryvttrwq46v0S2JzuU76o77Bab3siS+a58Iff01laD7zcYcvsIsN20+z0G3VAy4aLwVM6tPkNvNMl8V76QfHrr"
            + "qcH3+wy5vQPYbtr9PoNu+JhwUXgq51afgTeCXLwqXsg8vDVUx/stBtyAmaPVlPv9hdySEdme7FC+571Fv1WjhbhqThpGaBi2AhHe"
            + "G6GsEPEsXfdydSMMnHOJELFhaFMx8NXVQiBnayJEbBhwLpD1tR1i4oLNBAkOxZuRyQ2AcZDkAsl1K2T+TYToDEO7XeQF8Qci1ZV1"
            + "A0mLMaoq6WZSO4TeE7UbS+Mgybqy+5V0qykRYjgMeNtJjBIYil9auZdF6XZUIgQfhv2+CKIkByPOSrAF594ZvWtiYpTApLTXyul8"
            + "2Md2rhD8RnYa7P70EriR8XH1W/CAdGE+A1UuPJoteED6OOfCU7gEnz9+PXxEvpH5+ZO2hc8UfKLLCA9VC16TLm+/MLwpHteMu9mC"
            + "6M2KUTdYSnCxi8mbKi18pma8PqNuoVjwtnRl2wTyVflm2SReyNTtEQtOpHvvZPIuRQ8fqptIvwm82Gj7BL82Iex/I0gjv7fm9MFV"
            + "I5RGtiQXTFAeuicaXVbw0IJopzqI139MxvWBfP7LaOQy99CCaKdAiEl7TMYFggzay2hiEdbIXLJTHcBjOwXj2iD+2vdk/YxCCnD1"
            + "vWzEb7y16vxbb36yq9bGM/mDnw1+gNAQpCkQfjzoRzB+gNAQ0Byg3/7NAJOf/4kQsWFYMzHZNh2GyC2IVFdCOxp+gPoQtCMbK4Q9"
            + "CKWWrOMbKcKgkqRjnGYAufNpxznDELlacvuSdLTjBxgNAR7xaDH0YdjllHrxk458/AB0CO67n+iF9YJosxFrs6n3P+88S4uhT8Zo"
            + "bZy3u01ye4nwCxmq40dl5OCN7E/KR6Ej8WgNS/zMAJz0Z5au9HIh8TMDIPmXXisqfPZSoUVIDUKaBfY60YkQWwihTiS9SEj88gAC"
            + "G0kogjwEo4r8XSQSoF9D+h5She82u8AOUidCrIrUXqRvH0n8yQC0zSMWwh6EXEih1zt940jiwwGob3iRXSMYw5qJVGMNveUldoxY"
            + "CHsiOmtiezhu7X9twYB+ME71mRWjl0FFUss2OgEqCLVzWvbN3YAOrJSUSCUKqex/T7VOtAqz7+tsAtU8+vV4Oh/XpsJPeTV59B+6"
            + "UKiG1PGU03/KokdlS6hJNaTqeWXLid21wUgs0kin1eLZFSCMxCLdXAbqkvT5OrAmkP5LIwrVkDqedfpvifSobB01qYZUPa9sQbE7"
            + "cxiJRRrptBo9u8qHkVikm8tAXZJGXwfWBMJ/6sWACkLHUw7/MZcWlC2hHlQQaueULSR055USqUQhlVZzRxdxKZFKVPPo1yPp62Xe"
            + "NXm+UZOBrx9JdLid4xqLe+2EwzZjStiZ3Ml5mAaWJcMUg0Mv32KpgWXJoxyzw/YaeHTILqJ12TTTo8P0HtovaLtrsENzDXxbsmeH"
            + "hGhLNKoNzfnIyI3KsLyONbDUkTx3Yw/t14bTNywjowZuSabWRcrWZFvlYb8oWfZEDTyV7LwrmU5EDMfZ1tue/b4kug0pW0t2o7Yf"
            + "d+vTafW83m8u4Mv4LuTLny/oxbr7sAkzp/5SOoYuztm77Z0x801GG8jXv1VIqMasy3z9L3vJvDlDVWZj0t8gq8nUmFgmqcwicWHC"
            + "j+u97iQ2oILQws61QkVSq+cABlQQaue0eiBwC9o/oMJEKlFIZf+Aqk60CrN9QNUFqnn067F2QAV5NXnUSaxQDanjKadO4h6VLSHo"
            + "JJ5A9byy5YScxByJRRrptFo8chJzJBbp5jJQl6TPz53En/Ook1ihGlLHs06dxD0qW0fQSTyB6nllCwo5iTkSizTSaTV65CTmSCzS"
            + "zWWgLkmjnzuJP+dBJ7EBFYSOpxw6iVtQtoSYk3jAtHPKFhJxEmMilSik0mruxEmMiVSimke/HklfHzuJ/8HpTmIIvnrq2OI2zmA1"
            + "7oezTAQO5WHh6LXEHRxye2BZMkzx5Hh7AYwcMh5YljzKMbLHFMETd4yJ1mXTTE+sMU20X9B210C+GA98W7LmJKZoSzSqDctJDMmN"
            + "ypCcxEWw1JE0J3ET7deG0zckJ7EHbkmGTmLM1mRb5WG/KElOYg88ley8K4lOYg7H2dbbnv2+5DmJMVtLdqO2t8fDfnW3Pp4sB+QM"
            + "eN0A+d/v+8fdZvV1s78XHcSAOpU6dREDqiDVS6o1/YblWaF6Uhcsyga0JXTsz1aontRpTrUF1W+lY6FaldbWqKfzZj5V27vA5DIL"
            + "hywGlAitHbRgYlNi/2CSAqFAPtP9U8kyUJnj5lnapzTqeTaggtDpZFPDcwsKJr2F7IqUljbxDneBwnxLqxuYXSGvJI9aXQ2oIHQ6"
            + "39Tn2oKCWW8huyKl9U0so12gMN/S+gYeR8gryYMOR4HJZU4nG9obO0ww4R1iU6K0sIFXsMnjMy2t6rnBjeHei/t2OJ3+Wt0f7u52"
            + "L4jDXv7J7eJV8YVSkPmS/Kqf1MWr4nO5r9pO6/h+jxPZnmw15f0mOGH7hd62UM/RoXxb9U23XWS+K1/IPd2XmfKtmoeX0xk+mP9A"
            + "/fsNnmxEAbabdr/HoyvuJlwUnsq5Ved0+03mu/KF5NP9uSnfKnt4N53hg/kP1L/f58mGJGC7aff7PLrhbsJF4amcW3UOt2FdvCpe"
            + "yDzcpx3irYpnV9MRPZd7v+799g62pedoNeV+bycX3EW2JzuU73l96zfNtRBX/enDCA3ffiDCeye8FSKepetm/m6EwQWKRIjYMLSp"
            + "GFysqIVAt7QSIWLDgHOBLnC1Q0xucmWCBIfizcjkktc4SHKB5LoVuv+VCNEZhnaL3gviD0SqK+uOvRZjVFXSvft2CL0nanfxx0GS"
            + "dWX3K+mmfiLEcBjw9r4YJTAUv7RyL4vSLf9ECD4M+31R/AaAGUaclWALzr0zet8KEKMEJqW9Vi74u+P2/o+Ef9TCf775NqSXNw41"
            + "viS/t11r4VXxudz39m3b+JrF24TPhU8PVBS2J1utlemBSo/tr9DhgcoEHcq3vzC7Fy80cley49XV+K58oVwcr26fby1Txas7xQfz"
            + "by1Zdp1dpQPp/uOU24xHbLdi/CeqYDN24KLwVM4DS9R+rM6/R3CL7HijNb4rX6gYxxvd51trVfFGT/HB/FuLln1GQqUD6f5zldu6"
            + "R2y3YvznqmDrduCi8FTOA0vUfq7OvwNyi6x40S28Kl4oF8WL3sZby9Twog/pudxbixV9usWEz4X7D1Nsop+g1Vrxn6TcRK+wPdmh"
            + "fPsL036Iwu/uvAWHfP9CiBsnzO0I7dN4NcLtE+yI798MQIcwtkW4IWLD0KZCMUSEfP9uiNgw4FwIRq5GiLmPyw4SHIo3I3MT1yBI"
            + "coHkupXg4HJDdIYh+/6NIP5ApLpyff9CjFFVqb7/Rgi9J8q+/0GQZF3Z/Ur1/bshhsNQfP9KlMBQ/NLKvSyqvn83BB+G/b6o+/6d"
            + "MOKsBFtw7p3R9v0rUQKT0lkr3x8ylnmH+2G77SZ2sBPLmVymktLBjusVJvd6SuCBYK8CkEmsz3TS6hUBNyZJ4IFgrwiQo6HPdNLq"
            + "FQE+R3e4fbleBZCjuDZSSSmZfv/8iqKvvyH1yJ23SI98/Y3LPp/SwFPJk9d1Ea3LxqlGL+T+eZOI1mUPc802Cqro0f6ACg9I5xkf"
            + "bQJ04YkC97sJ+6UvoiuyvfMgDPeEwzrRzn0ou1Ul1jlPFa31Ku9cpwtP1InVT6zzGxHdlE3PazhdlO6Viv9yZZ3HiOi5bOv9yjsQ"
            + "acMjtRLoK/qLinYUcTiu94+Xb3LvNw+rx936dFo9r/ebS5zLkC+BLn++RFqsmQ97J4kg7/dStBi/cpUbx5v5cIaxVKvWMBbXQ2gY"
            + "/mQsrY35IF4h4aVRjZEahjMXxRDOIEIluxRhPITXvxjutOUY87m+EsJsUIMQzlyI7akfYT6EVHMahAgNQpmHTGf6PEBoOWRaXzGA"
            + "PQmNtfC83m1Wh/Ufq6+b/b34GoO4c7nT1YS4itxWXUtcU+5CDTvYitjJm4DKVeTiUhAe8wTbLAX+QL9BHT/FJa4pd55bsYUJT2YH"
            + "K4pVEtup2cNue3p6eaQ/bDbPp93L33E/YS3zXfkF15AdwBpA1Z0l8135wfxXHVwNfv/rICZcFO6mvf+BkBE8UPBtXyJgp3Ku1Tn9"
            + "zLIdQB6AkX/qwh8H0GoffmsZ8pNzkFgHgYZPPlpM4HLqAz0fXUVR6ab0WN61eqefAbYDyAMwJoBevBkH0MoffgsY8pNzkFgHgb5P"
            + "PqpL4HLqA30f3T5T6ab0WN61eoefqZX5rnwj+/Cm3ZSvVT77Vi3DB/MfqP9AuweffQVsN+2BXk+umZpwUXgq56DO9auoXoyrFtlp"
            + "iIZ/OBHivRlXi5FP1HVHcTvEwMwdiZEbiDcdA4d3MQa6iBGJkRsInQ90QaMfY3JRIxQlORhxViZ32uZRogsl2LnQrZRIjNZAtAu1"
            + "YpTAUKzqsu7aekFmtSXdvu3H8Bukdh93HiVaXXrvkq7qRmJMBwIv75phEoMJFFjwNVK64BuJIQxEf5MUv7mqxjFnJtmQg2+T3uVm"
            + "M0xiYlpr5nm3PurXRObUJWvtEnTs2p5TBak9c7VB9aQuWasF6G2hyrWQOVWQCqffuBAyhramX7gMssScXwUxqJ7UaU61NmVcARGg"
            + "mlAhoY0aPa33D5cvpat3PhysIrZwPCdxodzqMaiDVcT6ua0eet7G9s/0BSaXqaS0f3DfYXqF2j6m7yPlfCbqs/YvsWJiVSK9BiJx"
            + "Hblg+uldjy6XLit4s2OGDeSXLjF0fcOACkKdtHqPAXQ1w4AKQu2cRuqUPQvqyKpIejdE4jpyQQXQCyBdLl1b8LrHDBvIL11k6E6H"
            + "ARWEOmn1HgbovoYBFYTaOY3UKXsY1JFVkfDCiINVxILph7dCmli6rNgdkBHVzy1dXOSih8DkMpWUeg8AcolDYHKZcj4T9cl6f5l4"
            + "XaJ+hwSjr/oOmuSGO0Mkv7cxUHQsG9edF1XywAZjonXZONUDl8syGnn0TLQue5hr5MIroyfeOxcekM4zPnHXteGJAve7CfLPmeiK"
            + "bO32BYd7wmGdWFcsMLtVJdKFijJa61Xa9Yk2PFEnVj+R7kiY6KZseCNCoIvSvVLxX66k2w4mei7ber8SrzQYeCHrgZbov2N51xUE"
            + "upj0Tq1/OxzOT6uIPU1Df9j0mZIr+2geW5Bd3qXU0JroTK7LW5fLaOa58diebJ5uZsQZsKXiRracMTqUb6m+mY3AY3uyeb6Zt2DA"
            + "luobOQ3G6FC+pfpGJ6QaWhPNk42OTftoqbTJIeqUnMl1ua6fj9/v1bdsCIQCK9mkxJJE9kZHiVTiNI3sne0zIntzoEQqcZpH9m7w"
            + "GRE9oSAQCpwmET2DXvvs5Z+cly9LWuSJ5MHcG1RDqpTaTCkM3kQMqiFVz2v51eMqlV9J09Aj0eYqQ0/9CdVKr7nQ2HaNglXE+rnl"
            + "i41f+dHQI9HmYkOvhhOqlV5zsbG9IwWriPVzyxcbvlRhkSeSzZVGfj0MoFJqzWWGtrEMqiFVz+tkiflOew6/fnreZXc8BSb7w8k8"
            + "hgdzsmAoKLMnJg4VHpAupHxi4rgBZw4xFR6QPs45M4nV4SOXmIyPyDcyPzKK9fGZgk90GeYVU+E16Z4rX8Cb4nHNaN58Tm9WjOXP"
            + "r8PFLuZ59Pv4TM14fcZy6qvwtnTq1jf4qnyzbBIvZJZrX4UT6d47mendVwIo2Y+0y8R7mejhN/hq8hu1/2P95+Zhdf/9vLo/PD9v"
            + "ju7nkVX6+60lAi/sh7l4R3x131Glm9Jjea/uR5bp/U10D62JNtPd31MfoPUCb++uj8mZXAfruvblHQvcFEw/zuziVfG8UKg5ZoiX"
            + "Fif8fjOi53IvLVTkThLZnmw15fpDCDmWRLYnO5TvZH0rT6L5N6FvgOmnoV28Kp7XCrWODfHSCoVfj0b0XO6lpYq8eyLbk62mXH8U"
            + "IT+fyPZkh/KdrG/lUTT/IvUNMPwwtUo3pfNCgbbKGV1anOzb1QQey7u0RImn1UNros10648f4nP10JroTK6Dda08ecbfw75wD39u"
            + "H7b/ix8IwTDlqe3EmTYYNQaflXHLcYOYA+k1ITWGP4zwfPT60yxGp1GJEaZDcE4SaBxx3oUtYjeIMDPBtiXsHg+D2I2L7yejGOk5"
            + "sXsX3mo2Q0wH4Ww+0zji1Au7im4QYWaC3UvYcBwGsbsX34JEMdJzYncvvDtphpgOQtmvhGHEiefbUWoMPivBxsV3qmYx7LaF965I"
            + "iPB82D2LbmuJET4M4Wl7vnx3bP/Cf9ytT6fV83q/udipL+7ui5/68ueLoXrRhf9xEH6M1DB+GcJjo3jjOVcGsWTJlwax6PrPDEKf"
            + "iCXv/3gIr4zskqiGCA1CmYdiBGUImVpdCjAdwOvfy/bWcojxNF+JIDalQQRlHryW1A8wHkCoIQ0iZIZgzEGkG33OzyyDSLcr8uUJ"
            + "aK+BH4fDTj56dbiO3MoPGwlMBZd/UDpcR24gv+Ufi7e5g90TASoIddI62BDpQMWC7W959Jl2TiN1WtzFwMiySHyqLYElwaQI8G24"
            + "LhgvMHoDbsZN5BgvNmZmMKiGVCm14oOBGRIMqiFVz2umXuHTAbgIriGxbUACS4JJHeAbal0wXmX0VtqMm8gxXm7MLWJQDalSasXH"
            + "A3N8GFRDqp7XTL3CxwOwaVxDUl+Gw3XkkiKgt8aaXLzA4E2xETaQX7zMkBFHgApCnbSKjwRkphGgglA7p5E6hU+DsQPmr81ud/gR"
            + "tsAkgsQGsnDGE4ghDaN11BaIkRqGPxmNM7dGhPHJcyBGahjOXAzPn2cRQiU7O8K9EUAxxFgx5nNtWGL8EM5ciO2Ju2LqAVLNSfDF"
            + "SBGUech0JsEao0fIDMGehJtr4f8oUIVhCIUCAA==";

    private Mc263ExactStateProductionClosurePostData() {}

    static int blockId(String key) {
        return switch (key) {
            case "minecraft:acacia_fence" -> com.gameexpert.terrain.Blocks.ACACIA_FENCE;
            case "minecraft:acacia_stairs" -> com.gameexpert.terrain.Blocks.ACACIA_STAIRS;
            case "minecraft:bamboo_fence" -> com.gameexpert.terrain.Blocks.BAMBOO_FENCE;
            case "minecraft:birch_fence" -> com.gameexpert.terrain.Blocks.BIRCH_FENCE;
            case "minecraft:brick_stairs" -> com.gameexpert.terrain.Blocks.BRICK_STAIRS;
            case "minecraft:brick_wall" -> com.gameexpert.terrain.Blocks.BRICK_WALL;
            case "minecraft:brown_stained_glass_pane" -> com.gameexpert.terrain.Blocks.BROWN_STAINED_GLASS_PANE;
            case "minecraft:cherry_fence" -> com.gameexpert.terrain.Blocks.CHERRY_FENCE;
            case "minecraft:cobbled_deepslate_stairs" -> com.gameexpert.terrain.Blocks.POLISHED_DEEPSLATE_STAIRS;
            case "minecraft:cobbled_deepslate_wall" -> com.gameexpert.terrain.Blocks.POLISHED_DEEPSLATE_WALL;
            case "minecraft:cobblestone_wall" -> com.gameexpert.terrain.Blocks.COBBLE_WALL;
            case "minecraft:deepslate_brick_stairs" -> com.gameexpert.terrain.Blocks.DEEPSLATE_BRICK_STAIRS;
            case "minecraft:deepslate_brick_wall" -> com.gameexpert.terrain.Blocks.DEEPSLATE_BRICK_WALL;
            case "minecraft:deepslate_tile_wall" -> com.gameexpert.terrain.Blocks.DEEPSLATE_TILE_WALL;
            case "minecraft:diorite_stairs" -> com.gameexpert.terrain.Blocks.DIORITE_STAIRS;
            case "minecraft:diorite_wall" -> com.gameexpert.terrain.Blocks.DIORITE_WALL;
            case "minecraft:glass_pane" -> com.gameexpert.terrain.Blocks.GLASS_PANE;
            case "minecraft:granite_stairs" -> com.gameexpert.terrain.Blocks.GRANITE_STAIRS;
            case "minecraft:granite_wall" -> com.gameexpert.terrain.Blocks.GRANITE_WALL;
            case "minecraft:iron_bars" -> com.gameexpert.terrain.Blocks.IRON_BARS;
            case "minecraft:jungle_fence" -> com.gameexpert.terrain.Blocks.JUNGLE_FENCE;
            case "minecraft:jungle_stairs" -> com.gameexpert.terrain.Blocks.JUNGLE_STAIRS;
            case "minecraft:mossy_cobblestone_stairs" -> com.gameexpert.terrain.Blocks.MOSSY_COBBLE_STAIRS;
            case "minecraft:mossy_cobblestone_wall" -> com.gameexpert.terrain.Blocks.MOSSY_COBBLE_WALL;
            case "minecraft:mossy_stone_brick_stairs" -> com.gameexpert.terrain.Blocks.MOSSY_STONE_BRICK_STAIRS;
            case "minecraft:mossy_stone_brick_wall" -> com.gameexpert.terrain.Blocks.MOSSY_STONE_BRICK_WALL;
            case "minecraft:mud_brick_stairs" -> com.gameexpert.terrain.Blocks.MUD_BRICK_STAIRS;
            case "minecraft:mud_brick_wall" -> com.gameexpert.terrain.Blocks.MUD_BRICK_WALL;
            case "minecraft:orange_stained_glass_pane" -> com.gameexpert.terrain.Blocks.ORANGE_STAINED_GLASS_PANE;
            case "minecraft:pale_oak_fence" -> com.gameexpert.terrain.Blocks.PALE_OAK_FENCE;
            case "minecraft:polished_deepslate_stairs" -> com.gameexpert.terrain.Blocks.POLISHED_DEEPSLATE_STAIRS;
            case "minecraft:polished_deepslate_wall" -> com.gameexpert.terrain.Blocks.POLISHED_DEEPSLATE_WALL;
            case "minecraft:poplar_fence" -> com.gameexpert.terrain.Blocks.POPLAR_FENCE;
            case "minecraft:sandstone_stairs" -> com.gameexpert.terrain.Blocks.SANDSTONE_STAIRS;
            case "minecraft:sandstone_wall" -> com.gameexpert.terrain.Blocks.SANDSTONE_WALL;
            case "minecraft:smooth_sandstone_stairs" -> com.gameexpert.terrain.Blocks.SMOOTH_SANDSTONE_STAIRS;
            case "minecraft:spruce_stairs" -> com.gameexpert.terrain.Blocks.SPRUCE_STAIRS;
            case "minecraft:stone_brick_stairs" -> com.gameexpert.terrain.Blocks.STONE_BRICK_STAIRS;
            case "minecraft:stone_brick_wall" -> com.gameexpert.terrain.Blocks.STONE_BRICK_WALL;
            case "minecraft:waxed_cut_copper_stairs" -> com.gameexpert.terrain.Blocks.WAXED_CUT_COPPER_STAIRS;
            case "minecraft:waxed_oxidized_cut_copper_stairs" -> com.gameexpert.terrain.Blocks.WAXED_OXIDIZED_CUT_COPPER_STAIRS;
            case "minecraft:white_stained_glass_pane" -> com.gameexpert.terrain.Blocks.WHITE_STAINED_GLASS_PANE;
            case "minecraft:white_wool_stairs" -> com.gameexpert.terrain.Blocks.WHITE_WOOL_STAIRS;
            case "minecraft:yellow_stained_glass_pane" -> com.gameexpert.terrain.Blocks.YELLOW_STAINED_GLASS_PANE;
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
