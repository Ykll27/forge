package forge.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/**
 * Abre o seletor de arquivos do sistema para escolher decks. É uma Activity própria (transparente,
 * modo padrão) porque a Activity do jogo é "singleInstance", e nesse modo o Android pode devolver
 * "cancelado" imediatamente a um pedido de resultado. Entrega os arquivos à caixa de entrada e
 * traz o jogo de volta para a frente.
 */
public class DeckPickerActivity extends Activity {

    private static final int REQUEST_PICK = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            return; // recriada (ex.: rotação) enquanto o seletor está aberto: o resultado ainda virá
        }
        try {
            Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            pick.addCategory(Intent.CATEGORY_OPENABLE);
            pick.setType("*/*");
            pick.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"text/*", "application/octet-stream"});
            pick.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            startActivityForResult(pick, REQUEST_PICK);
        } catch (Exception e) {
            e.printStackTrace();
            backToGame();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_PICK && resultCode == RESULT_OK) {
            SharedDeckReader.deliverPicked(this, data);
        }
        backToGame();
    }

    private void backToGame() {
        Intent main = new Intent(this, Main.class);
        main.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        startActivity(main);
        finish();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }
}
