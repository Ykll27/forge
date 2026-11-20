# Forge PT-BR — análise, decisões e plano

Versão do Forge analisada: `Card-Forge/forge`, commit `9efd1699` (20/11/2026).
Este documento cobre a **Etapa 1** e registra o estado real de cada etapa: o que está
pronto e testado, o que está pronto mas ainda não foi verificado e o que falta.

## Estado das etapas

| Etapa | Estado | Evidência |
|---|---|---|
| 1. Análise | **Concluída** | este documento |
| 2. Fundação | **Concluída** | módulo `forge-ptbr` no build Maven; nenhuma dependência nova (Gson já vinha com o forge-gui); motor sem alterações |
| 3. Cartas em português | **Concluída no código e nos testes** | cliente/sincronização do Scryfall, cache, camadas de tradução; testes contra servidor HTTP local que imita o Scryfall. *Falta:* primeira sincronização real a partir do app |
| 4. Tradutor de efeitos | **Concluída (v1)** | métricas fora da amostra na seção 4; 12.942 faces processadas; teste de preservação de invariantes em 100% do arquivo gerado |
| 5. Interface mobile | **Iniciada** | botão PT/EN e selo de origem no zoom; grupo "Cartas em português" nas configurações. Restante na seção 6 |
| 6. Biblioteca | **Iniciada** | envio de decks por "Compartilhar"/"Abrir com", seletor de arquivos e lista colada, com nomes em português ([ENVIAR_DECKS.md](ENVIAR_DECKS.md)); restante na seção 6 |
| 7. Otimização | Parcial | carga preguiçosa do tradutor e geração no build (seção 3.4); restante na seção 6 |
| 8. Testes | Parcial | 40 testes novos + 23 preexistentes passando (seção 5) |
| 9. Distribuição | Planejada | workflow de CI que gera APK + `assets.zip`; passos obrigatórios na seção 6 |

**Ainda não verificado aqui:** a compilação completa de `forge-gui`/`forge-gui-mobile` e o APK.
O ambiente usado não tinha acesso ao Maven Central nem ao Android SDK. Os arquivos alterados
nesses módulos foram compilados contra o código-fonte do projeto e não apresentaram erros próprios,
mas a prova final é o workflow `.github/workflows/ptbr-build.yml` rodando no GitHub.

---

## 1. Como o Forge é construído

**Tecnologia.** Java 17, build Maven multimódulo. A interface mobile usa libGDX; a de desktop,
Swing. Android a partir da API 26 (Android 8), empacotado com `android-maven-plugin` e d8.

**Módulos relevantes.**

| Módulo | Papel |
|---|---|
| `forge-core` | Banco de cartas (`CardDb`, `CardRules`, `CardFace`), edições, decks, itens; `Localizer` (textos da interface) e `CardTranslation` (textos das cartas) |
| `forge-game` | **Motor de regras**: `Game`, `Match`, fases, pilha, `Card`, `SpellAbility`, gatilhos, efeitos de substituição e estáticos |
| `forge-ai` | Inteligência artificial dos oponentes |
| `forge-gui` | Camada comum das interfaces: `FModel` (inicialização), preferências, downloads de imagens, partidas online, modos de jogo |
| `forge-gui-mobile` | Interface libGDX (≈ 400 classes): telas, `MatchScreen`, `CardZoom`, configurações |
| `forge-gui-android` / `forge-gui-ios` | Empacotamento para cada plataforma |
| `forge-gui-desktop` | Interface Swing e a maior parte dos testes de integração |

**Motor de regras.** Cada carta é um script em `res/cardsfolder` (34.482 arquivos, 35.341 faces)
com custo, tipos, habilidades (`A:`, `T:`, `S:`, `SVar:`) e o texto `Oracle:`. O `CardFactory`
transforma o script em objetos `Card`/`SpellAbility`. O texto que o jogador vê vem da descrição
de cada habilidade e do Oracle; o motor não lê esse texto para decidir nada.

**Localização existente.**
- Interface: `res/languages/pt-BR.properties` com 3.754 das 3.917 chaves do inglês (468 faltando).
- Cartas: `res/languages/cardnames-pt-BR.txt` com nome em português para 25.756 faces e texto
  para 22.251. O `CardTranslation` associa linha a linha o texto traduzido às habilidades em jogo.
- Imagens: o Forge já baixa imagens do Scryfall no idioma preferido, inclusive `pt`.

**A tradução já é só exibição.** O `CardTranslation` é consultado apenas pelos métodos que montam
descrições. O teste `TranslationRulesIntegrityTest` (seção 5) confirma isso com partidas reais.

## 2. O que falta para um Forge em português

1. **Cartas sem texto oficial.** 12.942 faces não têm texto em português. Há duas razões:
   a Wizards [encerrou a impressão em português depois de Modern Horizons 3 (2024)](https://magic.wizards.com/en/news/announcements/changes-to-magic-product-languages-in-2024),
   e as coleções de 1993–2001 nunca foram impressas em português. Toda carta nova, portanto,
   depende de tradução automática.
2. **Arquivos fixos.** As traduções vêm dentro do pacote de recursos e só mudam com uma atualização do jogo.
3. **Sem origem nem correção.** Não há como saber se um texto é oficial nem como corrigir um erro.
4. **Sem "ver original"** no zoom do celular.
5. **Erro silencioso no arquivo oficial.** Linhas com tipo vazio (`Nome|Nome PT||`) apagavam
   o tipo inglês na tela. Corrigido: um campo vazio agora nunca sobrescreve outro.

**Limitações da arquitetura atual para celulares** (levantadas no código e no README do Forge,
que recomenda Android 11 e 6 GB de RAM):
- o banco de cartas inteiro fica em memória; há carregamento preguiçoso (`LOAD_CARD_SCRIPTS_LAZILY`), desligado por padrão;
- os recursos (`res/`) são baixados à parte (`assets.zip`) a partir do repositório original;
- a interface mobile é extensa e acoplada ao `forge-gui` (rede, xstream, netty).

## 3. Decisões

### 3.1 Evoluir a interface libGDX existente em vez de reescrevê-la
O motor e a IA são Java e rodam no aparelho; o `forge-gui-mobile` já é uma interface própria para
toque (não é a do desktop encolhida), com zoom por gestos e modos retrato/paisagem. Reescrever em
outra tecnologia duplicaria centenas de telas e componentes, o que o pedido proíbe ("evite
duplicar funcionalidades"). As melhorias de interface entram como mudanças incrementais nessas telas.

### 3.2 Módulo separado, sem acesso ao motor
`forge-ptbr` depende só do Gson e não conhece `forge-game`. Separação:

| Pacote | Responsabilidade |
|---|---|
| `forge.ptbr.model` | texto inglês de uma face; entrada traduzida; origem do texto |
| `forge.ptbr.store` | arquivos no formato `cardnames-*.txt`, gravação atômica |
| `forge.ptbr.scryfall` | cliente HTTP do Scryfall e sincronização incremental |
| `forge.ptbr.translator` | tradutor de efeitos (memória oficial + gramática) |
| `forge.ptbr.service` | orquestração: cache, geração, correções |
| `forge.ptbr.tools` | ferramenta de build que gera as traduções distribuídas |

A ponte com o jogo fica em `forge-gui` (`forge.gui.ptbr.PtBrIntegration`) e no `CardTranslation`.

### 3.3 Camadas de tradução, campo a campo
Ordem crescente de prioridade (cada campo, nome/tipo/texto, vem da camada mais alta que o tem):

1. `res/languages/cardnames-pt-BR-automatica.txt`: traduções automáticas geradas no build;
2. `<usuário>/ptbr/cardnames-pt-BR-automatica.txt`: regeneradas no aparelho;
3. `res/languages/cardnames-pt-BR.txt`: oficial, distribuído com o Forge;
4. `<usuário>/ptbr/cardnames-pt-BR-oficial.txt`: oficial, baixado do Scryfall;
5. `<usuário>/ptbr/cardnames-pt-BR-usuario.txt`: correções do jogador.

Tradução automática nunca cobre texto oficial. O `CardTranslation` informa a origem de cada texto
(`getTextSource`), o que alimenta o selo do zoom.

### 3.4 Desempenho
- As traduções automáticas são **geradas no build** e distribuídas prontas: o celular não gasta CPU nem bateria para isso.
- Na inicialização nada é minerado: o tradutor só é carregado se aparecer uma carta sem tradução em arquivo (personalizada, por exemplo), e só a partir do cache em disco.
- Regenerar no aparelho é incremental: cada linha guarda um hash do texto inglês e a versão do tradutor; só o que mudou é refeito.
- Arquivos são gravados de forma atômica (temporário + renomear), à prova de o Android encerrar o app no meio.
- Sem APIs Java ausentes no Android 8 (records convertidos em classes; sem `isBlank`, `Map.of` etc.).

## 4. Tradutor de efeitos

**Princípio:** primeiro reaproveitar redação oficial, depois compor por regras, e nunca inventar.
Uma frase que não pode ser traduzida com segurança fica em inglês e é contada.

1. **Memória de tradução minerada do texto oficial.** Os 22 mil textos oficiais são alinhados com o
   Oracle inglês (linha → frase → cláusula). Viram modelos com lacunas: números (`{N0}`), números por
   extenso com gênero (`dois`/`duas`), custos de mana, P/R e citações. Assim, "Compre dois cards."
   também ensina "Compre quatro cards.".
2. **Filtros de alinhamento.** Um par só entra na memória se os custos, números e P/R baterem e se os
   conceitos coincidirem ("target"↔"alvo", "exile"↔"exil", "create"↔"crie"...). Isso também descarta
   redações antigas ("Remova do jogo", "Coloque em jogo") e faz vencer a terminologia atual. Textos
   mais recentes pesam mais na votação.
3. **Terminologia minerada, não lembrada.** Palavras-chave, tipos, subtipos, nomes de fichas e de
   marcadores saem dos textos oficiais (ex.: Storm = "Rajada", Delve = "Esquadrinhar",
   Changeling = "Morfoloide", Shroud = "Manto", Treasure = "Tesouro").
4. **Redação de 2024.** "When this creature enters" e "When Nome enters the battlefield" viram a mesma
   chave; na volta, "esta criatura" ou o nome oficial em português.
5. **Gramática** para padrões comuns fora da memória (destruir/exilar/devolver, +X/+X até o fim do
   turno, causar dano, criar fichas, marcadores, gatilhos com sujeito composto), com gênero, número,
   contrações e a posição oficial de "alvo".
6. **Validação final.** Toda frase traduzida precisa preservar exatamente os invariantes do original;
   se não preservar, é descartada.

**Qualidade medida** (`CorpusQualityTest`, fora da amostra: memória construída só com cartas
anteriores a 2023, comparação com o texto oficial das 2.728 cartas posteriores):

| Métrica | Valor |
|---|---|
| Frases traduzidas | 63,4% |
| Linhas completas idênticas ao texto oficial | 80,3% |
| Similaridade média com o oficial (Levenshtein normalizado) | 0,981 |

Parte das "diferenças" é a própria fonte oficial em inglês ou com redação antiga.

**No alvo real** (faces sem texto oficial): 56,6% das frases traduzidas; 3.989 faces totalmente
traduzidas; 8.564 com texto publicado (o mínimo é 50% das frases, configurável). Mecânicas criadas
depois do fim da impressão em português (Station, Offspring, Plot, Warp...) não têm termo oficial e
ficam com o nome em inglês; o glossário (`forge-ptbr/src/main/resources/forge/ptbr/glossario.tsv`
e o glossário do usuário) permite adotar termos da comunidade.

## 5. Testes

| Teste | O que prova |
|---|---|
| `forge-ai` `TranslationRulesIntegrityTest` | **A tradução não altera o jogo.** Três partidas completas IA × IA (≈100 turnos) com a mesma semente terminam idênticas (vencedor, turno, vidas, zonas, nº de sorteios) em inglês e em português, mesmo com textos falsos nas camadas de usuário e oficial |
| `forge-core` `CardTranslationLayersTest` (5) | prioridade por campo, origem, provedor sob demanda, recarga, descrição de habilidade em jogo |
| `forge-ptbr` `CorpusQualityTest` (2) | pisos de qualidade contra o texto oficial; **todas** as traduções distribuídas preservam custos, números e P/R linha a linha |
| `forge-ptbr` `EffectTranslatorTest` (7), `TextUtilsTest` (5) | memória, lacunas, auto-referência, gramática, serialização |
| `forge-ptbr` `ScryfallSyncTest` (6) | paginação, impressão mais recente, faces, incremental, 404, 429 com `Retry-After`, desistência, cancelamento, cabeçalhos obrigatórios, intervalo de 500 ms |
| `forge-ptbr` `PtBrLocalizationServiceTest` (3) | geração só onde falta oficial, reaproveitamento do cache, uso offline, falha de rede sem perder dados, correções |
| `forge-ptbr` `TranslationFileTest` (4) | formato, escape, BOM, sobreposição |
| `forge-core` `DeckTextImporterTest` (7) | deck `.dck` real (60/60 cartas), lista sem metadados, MTG Arena com reserva, nomes e cabeçalhos em português, texto que não é deck, nomes que nunca sobrescrevem |
| `forge-game` (preexistentes, 23) | continuam passando |

## 6. Próximas etapas e critérios de conclusão

### Etapa 5 — Interface mobile
Base existente a aproveitar: `MatchScreen` com `VPlayerPanel`, `VField`, `VPhaseIndicator`,
`VLog`, `VStack`, `VPrompt`; `CardZoom` com gestos; modo paisagem (`UI_LANDSCAPE_MODE`).
Entregas:
1. barra inferior de navegação nas telas principais (Jogar, Decks, Biblioteca, Configurações);
2. mão em leque com ampliação ao tocar e segurar;
3. menu contextual por toque longo nas permanentes (ativar, atacar, ver texto);
4. indicador de fase/turno maior e legível em retrato;
5. histórico agrupado por turno, com filtro;
6. disposição do campo configurável (posição da mão, tamanho das cartas);
7. completar as 468 chaves faltantes do `pt-BR.properties`.

Critérios: alvos de toque ≥ 48 dp; capturas automáticas em 360×640, 412×915, 800×1280 e paisagem,
sem sobreposição; partida completa jogável só por toque; teste em aparelho com 3–4 GB de RAM.

### Etapa 6 — Biblioteca
Busca por nome em português ou inglês (o `AdvancedSearch` já consulta `CardTranslation`), filtros
por cor, raridade, tipo, custo e edição, alternância PT/EN, decks, acesso offline às cartas baixadas.
Critérios: busca por "Raio" e por "Lightning Bolt" encontra a mesma carta; filtros combinados testados;
funcionamento em modo avião.

### Etapa 7 — Otimização
Medir antes e depois (memória por perfil, tempo de abertura, bateria em 30 min de partida).
Candidatos: carregamento preguiçoso das cartas por padrão no Android; limites do cache de imagens por
classe de aparelho; descarte de texturas fora de tela. Critério: abrir e jogar em aparelho de 3 GB sem
encerramento por falta de memória.

### Etapa 8 — Testes
Testes de interface por resolução, partidas longas em série (estabilidade), cache e atualização da
base com o aparelho offline e online. Critério: CI verde em todos.

### Etapa 9 — Distribuição (obrigatório antes de publicar)
1. **Trocar `GITHUB_FORGE_URL`** (`ForgeConstants`) para o repositório do fork. Hoje o app baixa
   `assets.zip` e oferece atualizações do Forge original, o que traria recursos sem as traduções novas
   e poderia substituir o APK PT-BR pelo original.
2. Publicar, a cada versão, o APK **e** o `assets.zip` gerados pelo workflow `ptbr-build.yml`.
3. Usar um `applicationId` e um nome próprios (ex.: "Forge PT-BR") para instalar ao lado do original.
4. Chave de assinatura própria, guardada como segredo do repositório.
5. Tela "Sobre" com os avisos da seção 7.

## 7. Licenças e uso de dados

- **Código (GPL-3.0).** O Forge é GPL-3.0. Uma versão modificada distribuída precisa continuar
  GPL-3.0, publicar o código-fonte correspondente, manter os avisos de licença e indicar as
  modificações. Todo o código novo segue a mesma licença.
- **Textos e imagens das cartas** pertencem à Wizards of the Coast. A [Fan Content Policy](https://company.wizards.com/en/legal/fancontentpolicy)
  exige que o conteúdo de fã seja gratuito e traga o aviso de que não é oficial nem endossado pela
  Wizards. As traduções automáticas derivam do texto da Wizards e seguem a mesma regra.
  Na prática: sem cobrança, anúncios que bloqueiem acesso ou assinatura.
- **Scryfall.** As [regras da API](https://scryfall.com/docs/api) proíbem bloquear os dados atrás de
  pagamento e usar o nome do Scryfall como se ele endossasse o projeto. O app também precisa agregar
  valor além de repassar os dados. O cliente envia `User-Agent` e `Accept` e respeita os limites.
- **Aviso sugerido para a tela "Sobre":** "Forge PT-BR é conteúdo de fã não oficial, permitido pela
  Fan Content Policy. Não aprovado/endossado pela Wizards. Partes dos materiais usados são
  propriedade da Wizards of the Coast. ©Wizards of the Coast LLC. Dados de cartas em português:
  Scryfall."

Isto é uma leitura das políticas públicas, não aconselhamento jurídico.
