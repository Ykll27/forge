package forge.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/**
 * Ponto de entrada do app. Além do ícone, recebe decks de outros apps: "Compartilhar"
 * (um ou vários arquivos, ou texto) e "Abrir com" em arquivos .dck/.dec/.txt. O conteúdo é lido
 * aqui, enquanto a permissão de leitura dada pelo outro app ainda vale, e vai para a caixa de
 * entrada de decks; o jogo mostra a confirmação assim que a tela inicial estiver pronta.
 */
public class Launcher extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent main = new Intent(Launcher.this, Main.class);
        main.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        startActivity(main);

        SharedDeckReader.deliver(this, getIntent());

        finish();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        SharedDeckReader.deliver(this, intent);
    }
}
