# Fontes de Dados e Atribuicoes

Este documento registra as fontes usadas para gerar ou empacotar dados offline no app.
Antes de cada release publica, revise este arquivo e confirme se as licencas das fontes continuam compativeis com a distribuicao do APK.

Isto nao substitui revisao juridica. E apenas um registro tecnico de origem, transformacao e atribuicao.

## Assets de Mapa

### Natural Earth

Arquivos no app:

- `app/src/main/assets/map/ne_110m_land.min.geojson`
- `app/src/main/assets/map/ne_50m_land.min.geojson`
- `app/src/main/assets/map/ne_populated_places.json`
- `app/src/main/assets/map/ne_countries_labels.json`
- `app/src/main/assets/map/ne_admin1_regions.json`
- `app/src/main/assets/map/ne_country_boundaries_50m.json`

Fonte:

- Site: https://www.naturalearthdata.com/
- Repositorio: https://github.com/nvkelso/natural-earth-vector
- Termos/licenca: https://www.naturalearthdata.com/about/terms-of-use/

Uso no app:

- Massa de terra global.
- Divisas entre paises em resolucao 1:50m.
- Rotulos globais de paises, cidades e regioes administrativas.

Transformacoes aplicadas:

- Conversao para JSON/GeoJSON compacto.
- Reducao/simplificacao de pontos para renderizacao offline em Android.
- Selecao de campos necessarios para exibicao no mapa.
- As massas de terra e fronteiras mundiais podem ser regeneradas por `scripts/generate_map_assets.py`.

Observacao de licenca:

- Natural Earth declara os dados como public domain nos termos de uso oficiais.
- Mesmo assim, manter atribuicao no projeto por transparencia.

### Municipios Brasileiros

Arquivo no app:

- `app/src/main/assets/map/br_places.json`

Fonte:

- Repositorio: https://github.com/kelvins/Municipios-Brasileiros
- Arquivo usado: `json/municipios.json`

Uso no app:

- Lista de municipios brasileiros.
- Nome oficial do municipio.
- Codigo IBGE.
- Latitude e longitude de cada municipio.

Transformacoes aplicadas:

- Campos reduzidos para nome, latitude, longitude, codigo IBGE, UF e prioridade de rotulo.
- Filtro para manter uma unica entrada por municipio brasileiro.
- Serializacao compacta em JSON para uso offline.

Observacao de licenca:

- Verificar a licenca atual do repositorio antes de distribuir releases publicas.
- Se a licenca nao estiver explicita ou mudar, substituir por fonte oficial ou por base com licenca claramente compativel.

### IBGE - Malha Municipal Digital 2025

Arquivo no app:

- `app/src/main/assets/map/br_state_boundaries.json`

Fonte:

- Site: https://www.ibge.gov.br/geociencias/organizacao-do-territorio/malhas-territoriais/15774-malhas.html
- Download publico: https://geoftp.ibge.gov.br/organizacao_do_territorio/malhas_territoriais/malhas_municipais/municipio_2025/Brasil/BR_UF_2025.zip
- Arquivo usado: `BR_UF_2025.shp`

Uso no app:

- Divisas internas oficiais entre Unidades da Federacao brasileiras.

Transformacoes aplicadas:

- Leitura direta do shapefile oficial com `scripts/generate_map_assets.py`.
- Extracao apenas dos trechos compartilhados por duas ou mais UFs, removendo o contorno externo do Brasil desta camada.
- Encadeamento dos segmentos compartilhados em linhas continuas para evitar sobreposicao e serrilhado entre estados.
- Simplificacao leve e arredondamento de coordenadas para renderizacao offline sem deformar as divisas estaduais.
- Conversao para JSON compacto contendo apenas linhas internas de fronteira.

Observacao de licenca:

- O indice publico de downloads do IBGE informa que os arquivos ali disponiveis sao publicos.
- Manter atribuicao tecnica no projeto por transparencia e revisar os metadados do IBGE antes de releases publicas.

## Fontes Oficiais Alternativas Recomendadas

Caso seja necessario reduzir risco juridico ou aumentar precisao, preferir fontes oficiais:

- IBGE - Malhas territoriais: https://www.ibge.gov.br/geociencias/organizacao-do-territorio/malhas-territoriais.html
- IBGE - Localidades/estrutura territorial: https://servicodados.ibge.gov.br/api/docs/localidades

## Modelos de IA

O app possui suporte a modelo GGUF local configuravel. O URL padrao ou recomendado do modelo deve ser revisado no codigo antes de cada release.

Arquivo relacionado:

- `app/src/main/java/com/null0x/chat/ai/NullAiModelStore.kt`

Fonte atualmente usada como recomendacao tecnica:

- Qwen2.5 0.5B Instruct GGUF no Hugging Face: https://huggingface.co/bartowski/Qwen2.5-0.5B-Instruct-GGUF

Observacao de licenca:

- Modelos de IA podem ter licencas proprias e restricoes especificas.
- Antes de distribuir um APK que baixe ou recomende um modelo, revisar a licenca do modelo no Hugging Face.
- Nao incluir pesos de modelo no repositorio sem confirmar permissao de redistribuicao.

## Checklist Antes de Release

- Confirmar que cada asset offline tem fonte registrada neste documento.
- Confirmar a licenca/termos atuais de cada fonte.
- Guardar o link do arquivo original usado para gerar o asset.
- Registrar transformacoes relevantes, como simplificacao, filtros, arredondamentos e agregacoes.
- Evitar incluir dados sem licenca explicita quando houver alternativa oficial.
