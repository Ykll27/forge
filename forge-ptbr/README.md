# forge-ptbr — cartas em português para o Forge

Base de cartas em português do Brasil, tradutor de efeitos e cache. **Só exibição**: o motor de
regras continua lendo os scripts originais. Isso é verificado pelo teste
`forge-ai/.../TranslationRulesIntegrityTest`.

Decisões, métricas e plano completo: [`docs/ptbr/ANALISE_E_PLANO.md`](../docs/ptbr/ANALISE_E_PLANO.md).

## Como funciona no jogo

Com o idioma em **Português (Brasil)**, cada carta mostra:

1. a sua correção, se houver;
2. senão, o texto oficial baixado do Scryfall;
3. senão, o texto oficial que vem com o Forge;
4. senão, a tradução automática (frases que o tradutor não cobre ficam em inglês);
5. senão, o inglês.

No zoom da carta (celular), o selo no topo mostra a origem e um toque alterna para o original em inglês.

Em **Configurações → Cartas em português**:
- *Tradução automática*: liga/desliga a camada automática;
- *Mostrar origem do texto*: o selo do zoom;
- *Baixar/atualizar textos oficiais*: sincroniza com o Scryfall (incremental depois da primeira vez);
- *Regenerar traduções automáticas*: refaz só o que mudou e funciona offline.

## Arquivos

Em `res/languages/` (distribuídos):

| Arquivo | Conteúdo |
|---|---|
| `cardnames-pt-BR.txt` | textos oficiais do Forge (inalterado) |
| `cardnames-pt-BR-automatica.txt` | traduções automáticas geradas no build |

No diretório do usuário, `ptbr/`:

| Arquivo | Conteúdo |
|---|---|
| `cardnames-pt-BR-oficial.txt` | textos oficiais baixados (5ª coluna: data da impressão) |
| `cardnames-pt-BR-automatica.txt` | traduções regeneradas no aparelho |
| `cardnames-pt-BR-usuario.txt` | correções do jogador |
| `ptbr-glossario-usuario.tsv` | termos do jogador para o tradutor (mesmo formato do glossário) |
| `ptbr-memoria.tsv.gz` | memória de tradução em cache |
| `ptbr-estado.properties` | última sincronização, contagens |

Formato das linhas: `Nome em inglês|Nome PT|Tipo PT|Texto PT[|metadados]`, linhas separadas por `\n`
literal. Campo vazio = "não traduzido aqui"; nunca apaga o campo de outra camada.

### Corrigir uma tradução

Acrescente uma linha em `ptbr/cardnames-pt-BR-usuario.txt`, preenchendo só o que quer mudar:

```
Summit Sentinel|||Quando esta criatura morrer, compre um card.
```

O código pode fazer o mesmo com `PtBrIntegration.saveCorrection(...)`.

### Glossário

`src/main/resources/forge/ptbr/glossario.tsv` (e `ptbr/ptbr-glossario-usuario.tsv`), TSV:

```
KEYWORD	offspring {M0}	prole {M0}	comunidade	mecânica de 2024 sem impressão em PT
```

Tipos: `KEYWORD`, `ABILITY_WORD`, `SUBTYPE`, `TYPE`, `TOKEN`, `COUNTER`. Status `oficial` ou
`comunidade`. Termos minerados de cartas oficiais sempre vencem o glossário.

### Traduções revisadas (cartas inteiras)

As coleções a partir de Bloomburrow (agosto de 2024) não foram impressas em português, então não
há texto oficial nem frases parecidas no acervo para a memória reaproveitar. Para elas,
`src/main/resources/forge/ptbr/revisadas.txt` traz o texto inteiro da carta, escrito a partir do
Oracle com a terminologia oficial (`nome em inglês|texto`, `\n` entre linhas). O tradutor usa esse
texto antes da memória e das regras; ele continua marcado como tradução automática e nunca passa
por cima de texto oficial ou de correção do jogador.

`RevisedTextsTest` exige que cada carta exista, não tenha texto oficial e mantenha, linha a linha,
os custos, números, P/R e X do Oracle. Hoje o arquivo cobre Bloomburrow (187 cartas que a memória
não traduzia inteiras). Para acrescentar uma coleção, inclua as cartas no arquivo (em ordem
alfabética), rode os testes e regenere as traduções distribuídas.

## Regenerar as traduções distribuídas

Depois de mudar o tradutor ou o arquivo oficial:

```bash
mvn -pl forge-ptbr -am -DskipTests install
java -cp forge-ptbr/target/classes:$HOME/.m2/repository/com/google/code/gson/gson/2.13.2/gson-2.13.2.jar \
     forge.ptbr.tools.GenerateAutomaticTranslations forge-gui/res --force
```

A saída é determinística; o CI (`ptbr-build.yml`) falha se o arquivo commitado estiver desatualizado.
Ao mudar regras do tradutor, aumente `EffectTranslator.VERSION` para invalidar os caches dos aparelhos.

## Testes

```bash
mvn -pl forge-core,forge-game,forge-ai,forge-ptbr -am test
```

Utilitários para quem mexe no tradutor (pasta de testes, executáveis com `main`):
- `QualityReport [AAAA-MM-DD] [N]`: avaliação fora da amostra e as N piores divergências;
- `TargetCoverage [N]`: cobertura nas cartas sem texto oficial e as N frases mais frequentes sem tradução;
- `Probe "frase" "@Nome da carta"`: como uma frase ou carta é traduzida.
