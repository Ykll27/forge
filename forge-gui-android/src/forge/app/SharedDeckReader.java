package forge.app;

import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import forge.deck.DeckTextImporter;
import forge.gui.ptbr.DeckInbox;

/**
 * Lê decks enviados ao app pelo Android ("Compartilhar", "Abrir com", seletor de arquivos)
 * e entrega o texto à {@link DeckInbox}. O conteúdo é lido pelo ContentResolver, que funciona
 * com o armazenamento restrito do Android 11+ sem pedir permissão de arquivos.
 */
final class SharedDeckReader {

    private SharedDeckReader() {
    }

    /** Trata SEND, SEND_MULTIPLE e VIEW. Devolve quantos itens foram entregues. */
    static int deliver(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) {
            return 0;
        }
        String action = intent.getAction();
        int delivered = 0;
        if (Intent.ACTION_VIEW.equals(action)) {
            if (intent.getData() != null && read(context, intent.getData())) {
                delivered++;
            }
            return delivered;
        }
        if (Intent.ACTION_SEND.equals(action) || Intent.ACTION_SEND_MULTIPLE.equals(action)) {
            for (Uri uri : streams(intent)) {
                if (read(context, uri)) {
                    delivered++;
                }
            }
            if (delivered == 0) {
                // texto compartilhado diretamente (ex.: "Exportar → Compartilhar" de um site de decks)
                CharSequence text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT);
                if (text != null && text.length() > 0) {
                    String subject = intent.getStringExtra(Intent.EXTRA_SUBJECT);
                    DeckInbox.offer(subject != null && !subject.trim().isEmpty() ? subject : "Deck compartilhado",
                            text.toString());
                    delivered++;
                }
            }
        }
        return delivered;
    }

    /** Resultado do seletor de arquivos (um ou vários arquivos). */
    static int deliverPicked(Context context, Intent data) {
        if (data == null) {
            return 0;
        }
        List<Uri> uris = new ArrayList<>();
        ClipData clip = data.getClipData();
        if (clip != null) {
            for (int i = 0; i < clip.getItemCount(); i++) {
                if (clip.getItemAt(i).getUri() != null) {
                    uris.add(clip.getItemAt(i).getUri());
                }
            }
        } else if (data.getData() != null) {
            uris.add(data.getData());
        }
        int delivered = 0;
        for (Uri uri : uris) {
            if (read(context, uri)) {
                delivered++;
            }
        }
        return delivered;
    }

    @SuppressWarnings("deprecation")
    private static List<Uri> streams(Intent intent) {
        List<Uri> uris = new ArrayList<>();
        if (Intent.ACTION_SEND_MULTIPLE.equals(intent.getAction())) {
            ArrayList<Uri> list = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
            if (list != null) {
                uris.addAll(list);
            }
        } else {
            Uri uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            if (uri != null) {
                uris.add(uri);
            }
        }
        if (uris.isEmpty() && intent.getClipData() != null) {
            ClipData clip = intent.getClipData();
            for (int i = 0; i < clip.getItemCount(); i++) {
                if (clip.getItemAt(i).getUri() != null) {
                    uris.add(clip.getItemAt(i).getUri());
                }
            }
        }
        return uris;
    }

    /** Lê um arquivo e o entrega; arquivos binários ou ilegíveis são ignorados. */
    private static boolean read(Context context, Uri uri) {
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            if (in == null) {
                return false;
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            int limit = DeckTextImporter.MAX_CHARS * 2;
            while ((n = in.read(buf)) > 0 && out.size() < limit) {
                out.write(buf, 0, n);
            }
            byte[] bytes = out.toByteArray();
            for (int i = 0; i < Math.min(bytes.length, 4096); i++) {
                if (bytes[i] == 0) {
                    return false; // imagem, PDF etc.: não é uma lista de deck
                }
            }
            DeckInbox.offer(displayName(context, uri), decode(bytes));
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /** UTF-8; se não for UTF-8 válido (arquivos antigos do Windows), Latin-1. */
    private static String decode(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            return new String(bytes, StandardCharsets.ISO_8859_1);
        }
    }

    private static String displayName(Context context, Uri uri) {
        try (Cursor c = context.getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0 && c.getString(idx) != null) {
                    return c.getString(idx);
                }
            }
        } catch (Exception ignored) {
            // alguns provedores não informam o nome
        }
        String last = uri.getLastPathSegment();
        return last != null ? last : "Deck recebido";
    }
}
