# Roteiro de toques e capturas (coordenadas da tela deitada do emulador, 1280x640).
# Funções: espera <s>, foto <nome>, toque <x> <y>, toque_texto "<texto>", deslize x1 y1 x2 y2, estado <nome>.

espera 15
foto 01-aviso
toque 443 403           # "Don't remind me next time."
toque_texto "Run Forge.."
espera 25
foto 02-download
toque 52 605            # botão "Download" dos recursos
espera 50
foto 03-depois-do-download
toque_texto "Run Forge.."   # o Forge reinicia depois de instalar os recursos
espera 40
foto 04-carregando
toque_texto "Run Forge.."
espera 40
foto 05-inicio
espera 30
foto 06-inicio
toque 640 466           # Classic Mode
espera 25
foto 07-classico
espera 10
foto 08-classico
toque 1228 605          # "Not Now" (índice de imagens)
espera 5
foto 09-lobby
toque 661 168           # deck do jogador 1
espera 10
foto 10-escolher-deck
