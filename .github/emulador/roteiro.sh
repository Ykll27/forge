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
toque 248 27            # voltar da escolha de deck
espera 4
toque 75 565            # Settings
espera 8
foto 11-config
toque 690 110           # Language
espera 4
foto 12-idiomas
toque 700 158           # pt-BR
espera 4
foto 13-idioma-escolhido
adb shell am force-stop forge.app
espera 2
adb shell am start -n forge.app/.Launcher
espera 50
foto 14-reiniciado
espera 30
foto 14b-reiniciado
toque 640 466           # Modo Clássico
espera 25
foto 15-lobby-pt
toque 662 541           # Início
espera 30
foto 16-partida
espera 15
foto 17-partida
toque 51 605            # Jogar (começar)
espera 12
foto 18-mao
deslize 300 500 300 500 1500   # segurar uma carta da mão (zoom)
espera 4
foto 19-zoom
deslize 640 200 640 500 300    # deslizar para baixo: visão de detalhes
espera 3
foto 20-detalhes
