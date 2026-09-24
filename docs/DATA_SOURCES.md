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
- A base mundial de localidades usa `ne_10m_populated_places_simple`, versao 5.1.2.
- Os campos compactos de cidade preservam escala cartografica, populacao, capital nacional e zoom minimo para priorizacao progressiva dos rotulos.
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

### GeoNames - localidades de grandes economias

Arquivo combinado no app:

- `app/src/main/assets/map/ne_populated_places.json`

Fonte:

- Download: https://download.geonames.org/export/dump/cities500.zip
- Licenca: Creative Commons Attribution 4.0

Escopo e transformacoes:

- Localidades com mais de 500 habitantes e sedes administrativas ate PPLA3.
- Recorte dos Estados Unidos, China, Alemanha, Japao, India, Reino Unido, Franca, Italia e Canada.
- O Brasil permanece coberto pela base municipal especifica ja documentada.
- Apenas nome, coordenadas, populacao, prioridade e zoom minimo sao mantidos.
- Entradas coincidentes com o Natural Earth sao deduplicadas pelo nome e coordenadas arredondadas.
- O gerador rejeita o asset combinado se ele ultrapassar 15 MB.
- O recorte usa como criterio as dez maiores economias no ranking de PIB nominal de 2023 do Banco Mundial, incluindo o Brasil.
- Ranking: https://datacatalogfiles.worldbank.org/ddh-published/0038130/DR0046441/GDP.pdf

### U.S. Census Bureau - localidades dos Estados Unidos

Arquivo combinado no app:

- `app/src/main/assets/map/ne_populated_places.json`

Fonte:

- Gazetteer nacional de Places 2025: https://www2.census.gov/geo/docs/maps-data/data/gazetteer/2025_Gazetteer/2025_Gaz_place_national.zip

Transformacoes:

- Inclusao dos nomes oficiais e pontos internos representativos de todos os `Places` publicados no arquivo nacional.
- Conversao das coordenadas para o formato compacto usado pelo mapa.
- Deduplicacao contra Natural Earth e GeoNames antes da escrita do asset offline.

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

## Checklist Antes de Release

- Confirmar que cada asset offline tem fonte registrada neste documento.
- Confirmar a licenca/termos atuais de cada fonte.
- Guardar o link do arquivo original usado para gerar o asset.
- Registrar transformacoes relevantes, como simplificacao, filtros, arredondamentos e agregacoes.
- Evitar incluir dados sem licenca explicita quando houver alternativa oficial.
