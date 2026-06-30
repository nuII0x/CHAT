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
- `app/src/main/assets/map/ne_country_boundaries_110m.json`

Fonte:

- Site: https://www.naturalearthdata.com/
- Repositorio: https://github.com/nvkelso/natural-earth-vector
- Termos/licenca: https://www.naturalearthdata.com/about/terms-of-use/

Uso no app:

- Massa de terra global.
- Divisas entre paises em baixa resolucao.
- Rotulos globais de paises, cidades e regioes administrativas.

Transformacoes aplicadas:

- Conversao para JSON/GeoJSON compacto.
- Reducao/simplificacao de pontos para renderizacao offline em Android.
- Selecao de campos necessarios para exibicao no mapa.

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

### geodata-br-states

Arquivo no app:

- `app/src/main/assets/map/br_state_boundaries.json`

Fonte:

- Repositorio: https://github.com/giuliano-macedo/geodata-br-states
- Arquivo usado: `geojson/br_states.json`

Uso no app:

- Divisas internas entre estados brasileiros.

Transformacoes aplicadas:

- Simplificacao de geometria.
- Arredondamento de coordenadas.
- Deduplicacao das linhas compartilhadas entre estados para renderizar apenas uma divisa interestadual.
- Remocao do contorno externo do Brasil nesta camada; o contorno entre paises fica na camada Natural Earth.
- Conversao para JSON compacto contendo apenas segmentos internos de fronteira.

Observacao de licenca:

- Verificar a licenca atual do repositorio antes de distribuir releases publicas.
- Se necessario, substituir por malhas oficiais do IBGE.

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
