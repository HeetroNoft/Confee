package fr.heetstudio.confee.format;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/**
 * Decoded content of a config file plus what is needed to write it back byte for byte (spec 7.2):
 * UTF-8 BOM presence and line separator.
 * <p>Internal: may change without notice.</p>
 *
 * @param text          file content without the BOM
 * @param bom           whether the file started with a UTF-8 BOM
 * @param lineSeparator {@code "\r\n"} or {@code "\n"}, from the first line break of the file ({@code "\n"} if none)
 */
public record SourceText(String text, boolean bom, String lineSeparator) {

    private static final byte[] BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    /**
     * Strictly decodes UTF-8 bytes.
     *
     * @throws CharacterCodingException if the bytes are not valid UTF-8: such a file cannot be
     *                                  re-encoded identically, so it must not be edited
     */
    public static SourceText decode(byte[] bytes) throws CharacterCodingException {
        boolean bom = bytes.length >= 3 && bytes[0] == BOM[0] && bytes[1] == BOM[1] && bytes[2] == BOM[2];
        int offset = bom ? 3 : 0;
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        CharBuffer chars = decoder.decode(ByteBuffer.wrap(bytes, offset, bytes.length - offset));
        String text = chars.toString();
        return new SourceText(text, bom, detectLineSeparator(text));
    }

    /** A new file: no BOM, {@code \n} line separator. */
    public static SourceText ofNew(String text) {
        return new SourceText(text, false, "\n");
    }

    /** Encodes {@code newText} with the same BOM setting as the original file. */
    public byte[] encode(String newText) {
        byte[] body = newText.getBytes(StandardCharsets.UTF_8);
        if (!bom) {
            return body;
        }
        byte[] out = new byte[body.length + 3];
        System.arraycopy(BOM, 0, out, 0, 3);
        System.arraycopy(body, 0, out, 3, body.length);
        return out;
    }

    public static String detectLineSeparator(String text) {
        int lf = text.indexOf('\n');
        return lf > 0 && text.charAt(lf - 1) == '\r' ? "\r\n" : "\n";
    }
}
