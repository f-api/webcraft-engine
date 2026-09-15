package com.gameexpert.terrain.mc.feature;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.zip.GZIPInputStream;

/**
 * Generated official POST {@code SpeleothemBlock#updateShape} closure for the pinned 26.3
 * pointed-dripstone and sulfur-spike families.
 */
final class Mc263SpeleothemShapeClosureData {
    static final int SUBJECT_COUNT = 40;
    static final int CLASS_COUNT = 42;
    static final int VERDICT_COUNT = 48;
    static final int STURDY_CLASS = 40;
    static final int PLAIN_CLASS = 41;
    static final String DIGITS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
    static final String RECEIPT_SHA256 = "94315a658b8c31b939c99316965d5f03716a271e757601b04c22866c7429fcd7";
    static final String PACKED_RECEIPT_SHA256 = "36b7c37c68738f05add0b5bd6e16da2f5413deda59fb71d87b94c434806b45c8";
    private static final int ROW_COUNT = 208;
    private static final String GZIP_BASE64 = "H4sIAAAAAAAC/+3daW8bNxDG8dfsZ0kAcblngbzonTRNr7TphcJwLNlRbcuujuTrd0YbS1ygQsbWBiCI/89MAHnpR8SupRHjjPXS"
            + "Tdz1fDE7W56erz+9vZkv1rPpyXQ5v12tbxazv9Zv5meXi9lq9eT16Wr26O1suZ6fnV6dTOfL2dl6frN4Mr15t3j07nQ9W17dXFzM"
            + "pk/OT69Ws78/een82Mnr5WYbXBwZvLn9/wWHcXPvllsaY8+Xm9V6c32/k1x9hPC7hdfHZx861c3o0XeLbo3J1/Pp9Oqe39Hd+Nl3"
            + "y/aTo7MPnWvvx47eLdr6SFzPb+/53BFGTt4tuTwu+OBJrkbN3S23tseeXM+WF/d9jm4+Sv5u+e0Y8QfPefcR0ndFJn5ArjZX55vl"
            + "yep2fnlcTSz8iKm7pRYPDz10boswWuZumeWHIx9UA4tq3ODdguujcg+e2mbM2N1i2w+nPqTmFd2ouXfLDZNjYg++gvMjpu6Wanh4"
            + "3bvAhTBe6G6h5YMzD57QaqzI3SJrU+JDilloxo7eLbo9Mvng+e3GDe4X/Ep2dRPZfxXy92MvN/32o5A/ldwq9NWmfuL90aAvEfsP"
            + "PVzq6zo9dHe80k9EabW+PNtO6L+g0dv7+FZfZMVf3+knovvTe6/iAL37KlqB3l09WH/QVyr7u9C8dhBZ6auBKKHezt0fb7Sgx4mt"
            + "1uIosdMyGn2FTC9ClCizizJOkOlFFZ/VoAUjTij1qT5KqPRJOk6o9dk1Smjc4B5bfZaMAzt9gttPkKNheKXkcBhcKrkVBtdKboX4"
            + "YslzRhheLXnIh8HlkkdsGFwueZyF+HLJoyMMLpd8V4f4cpUTnRNFll7nRN9yhRuc2zK4+NSWpRuc2bJy8Yktazc4r2Xj9qf1xfax"
            + "8BUAAJnaljrvPgMAIAH2AmbPlFKnm8FvAADI1LbUefc5AAAJsBcwe6aUOv0Z3k8AACTgmZk9c1vqvHsGAECmpNTp/0B9DQBAAp6b"
            + "2TO3pc675wAAZEpKXUlfHQAg7746bU7/FwCABNgLmD1TSl1FXx0AIO++Ov0VY0sAABJgL2D2TCl1tezqVgAAJMDeQmDP3JY6+uoA"
            + "AFn31TWyq1sDAJAAewuBPXNb6uirAwBk3VfX0lcHAMi7r07ftnUDAEAC7AXMnimlrqOvDgCQd19dJ7u6twAAJMBewOyZ+tasE9nW"
            + "fQEAQALsPQT2zL7W0VkHAMi6s8572dd9CQBAAuxdBPbMvtbRWwcAyLq3zhc01wEA8m6uk1rn3dcAACTAXsHsmVrrAu11AIC82+uk"
            + "1nn3FACABNgrmD1Ta10p+7pvAQBIgL2TwJ7Z1zr66wAAeffXVbKv+w4AgATYOwnsmX2to78OAJB3f11Nfx0AIPP+ulr2dS8AAEiA"
            + "vYLZM7XWNfTXAQAy769rZF/3PQAACbBXMHum1rpW9nU/AACQAHsngT2zr3X01wEA8u6v62Rf9yMAAAmwdxLYM/taR38dACDr/rpi"
            + "Ivu6GQAAmeprnXc/AwCQAHsFs2dqrfOyr7sAACBTfa3z7iUAAAmwVzB7pta6QvZ1vwAAkIC5mT2zr3XezQEAyJTWuiD7ul8BAEjA"
            + "pZk9s6913l0CAJAprXUl/XUAgMz760rZ170CACAB9gpmz9RaV9FfBwDIvL+ukn3dbwAAJMBeweyZWutq2df9DgBAAuydBPbMvtbR"
            + "XwcAyLu/rpF93R8AACTA3klgz+xrHf11AIC8++ta+usAAJn317Wyr/sTAIAE2CuYPVNrXUd/HQAg8/66TvZ1pwAAJMBeweyZUuvC"
            + "RPZ1ZwAAJMDeSWDP7Gsd/XUAgKz764KXfd0UAIAE2DsJ7Jl9raO/DgCQdX9dKOivAwDk3V8ntc67cwAAEmCvYPZMrXWB/joAQN79"
            + "dVLrvHsDAEAC7BXMnqm1rpR93T8AACTA3klgz+xrHf11AIC8++sq2dddAQCQAHsngT2zr3X01wEA8u6vq+mvAwBk3l9Xy77uGgCA"
            + "BNgrmD1Ta11Dfx0AIPP+ukb2dQsAABJgr2D2TK11rezrbgAASIC9k8Ce2dc6+usAAHn313Wyr7sFACAB9k4Ce2Zf6+ivAwBk3F/3"
            + "VDZ1+vHYb/+S2/rWrdvb3lVys3DF4HBwIT5cunJwuHJVfLh29eBw45r4cOvaweHOdfFhP9ExWJzXEU8pdAymBB3xlFLHYEqlI55S"
            + "6xhMaXTEU1odgymdjvhUTXTEUwqvI55S6BhMCTriKaWOwZRKRzyl1jGY0uiIp7Q6BlM6HdGUMNExuLa+f5f6/ZSif3vDaEro3xhj"
            + "P6Xsf6NqNKXqfxfPfkrdN3FGU5r+v//up7T9z42jKV3/Lw7vp/wHNta64TY9AgA=";

    private Mc263SpeleothemShapeClosureData() {}

    static List<String> rows() {
        try (GZIPInputStream input = new GZIPInputStream(new ByteArrayInputStream(
                Base64.getDecoder().decode(GZIP_BASE64)))) {
            List<String> values = new String(input.readAllBytes(), StandardCharsets.US_ASCII)
                    .lines().toList();
            if (values.size() != ROW_COUNT) {
                throw new ExceptionInInitializerError("speleothem shape closure row drift");
            }
            return values;
        } catch (IOException failure) {
            throw new ExceptionInInitializerError(failure);
        }
    }
}
