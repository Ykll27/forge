# Enviar decks para o Forge PT-BR

Três jeitos de colocar um deck no jogo, sem copiar arquivos para pastas do Android. Os três
terminam na mesma tela de confirmação.

## 1. De outro app (o mais fácil)

No WhatsApp, Telegram, Drive, Arquivos ou no navegador:

- toque no arquivo `.dck`, `.dec` ou `.txt` → **Abrir com → Forge**, ou
- **Compartilhar → Forge** (aceita vários arquivos de uma vez), ou
- num site de decks (Moxfield, Archidekt, MTGGoldfish...), **Exportar → Compartilhar texto → Forge**.

O Forge abre e mostra o deck. Se o jogo ainda estiver carregando, o deck fica guardado e aparece
quando a tela inicial abrir.

## 2. Arquivo, de dentro do jogo

**Decks → Novo deck → Importar arquivo de deck...** abre o seletor de arquivos do Android
(não pede permissão de armazenamento). Dá para escolher vários arquivos.

## 3. Lista copiada

Copie a lista em qualquer lugar e use **Decks → Novo deck → Colar lista copiada**.

## A tela de confirmação

Mostra o nome do deck, o total de cartas por seção (principal, reserva, comandante) e as linhas
que não foram reconhecidas. Opções: **Salvar**, **Salvar e abrir** (abre no editor) ou **Descartar**.
Um deck nunca sobrescreve outro: se o nome já existe, é salvo como "Nome (2)".

## Formatos aceitos

- arquivo do Forge (`.dck`, com `[metadata]`, `Name=`, `[Main]`, `[Sideboard]`);
- MTG Arena (`4 Clifftop Lookout (BLB) 168`, cabeçalhos `Deck`/`Sideboard`);
- MTGO/`.dec`, `SB: 2 Carta`, listas simples (`4 Carta`, `4x Carta`);
- **nomes em português** (com o jogo em português): `4 Raio`, `4x contramágica`, `10 ilha`.
  Maiúsculas e acentos não importam;
- cabeçalhos em português: `Principal`, `Reserva`, `Comandante`; nome com `Nome:`.

Cartas lançadas depois de 2024 não têm nome oficial em português; nesses casos use o nome em inglês.

## Onde está no código

| Parte | Arquivo |
|---|---|
| Reconhecimento do texto (testado) | `forge-core/.../deck/DeckTextImporter.java`, `DeckRecognizer` |
| Nome traduzido → nome inglês | `CardTranslation.getEnglishName` |
| Fila de decks recebidos e salvamento | `forge-gui/.../gui/ptbr/DeckInbox.java` |
| Confirmação e menu "Novo deck" | `forge-gui-mobile/.../deck/ReceivedDecks.java`, `FDeckChooser` |
| Android: compartilhar/abrir com | `forge-gui-android/.../app/Launcher.java`, `SharedDeckReader.java`, `AndroidManifest.xml` |
| Android: seletor de arquivos | `DeckPickerActivity.java` (Activity própria, porque a do jogo é `singleInstance`) |

Testes: `forge-core/src/test/java/forge/deck/DeckTextImporterTest.java` (inclui o deck
"Sapos Puro 60" como arquivo de teste).
