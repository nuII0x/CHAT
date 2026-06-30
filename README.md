Null0x-Chat — Melhorias e Correções Pendentes

Este arquivo serve para anotar ideias, melhorias, correções e ajustes futuros do aplicativo.

---

🛠 Correções Pendentes

- [ ] Testar APK em outro aparelho Android.

---

📦 Publicação

- [ ] Criar release no GitHub.
- [ ] Gerar APK assinado.
- [ ] Adicionar sistema de atualização via GitHub Releases.
- [ ] Resolver como distribuir o app via torrent automaticamente dentro do app mesmo.

## Instruções de instalação

1. Abra o projeto no Android Studio.
2. Conecte um aparelho Android com depuração USB habilitada, ou use um emulador.
3. Execute `./gradlew installDebug` para instalar a versão de desenvolvimento.
4. Se preferir gerar um APK, use o build do Android Studio e instale o pacote no aparelho.

## Fontes de dados

As fontes usadas para gerar ou empacotar dados offline do app ficam em [`docs/DATA_SOURCES.md`](docs/DATA_SOURCES.md).
Revise esse arquivo antes de publicar releases, principalmente quando incluir mapas, bases territoriais ou modelos de IA.

## Versionamento automatico

O projeto usa `version.properties` para manter `major.minor.patch`.

Para gerar release e atualizar a versao ao mesmo tempo:

```bash
./gradlew assembleRelease -PreleaseType=major
./gradlew assembleRelease -PreleaseType=minor
./gradlew assembleRelease -PreleaseType=security
```
O versionamento segue o modelo x.y.z
`major` aumenta `x` e zera `y` e `z`, `minor` aumenta `y` e zera `z`, e `security` aumenta apenas `z`.

---

🧪 Testes

- [ ] Testar chat comigo mesmo.
- [ ] Testar envio com outro aparelho.
- [ ] Testar app sem internet.
- [ ] Testar mudança de Wi-Fi para dados móveis.
- [ ] Testar reinício do celular.

---

📝 Anotações Rápidas

- 
- 
- 

---

## Timeline de desenvolvimento

### v1.0 — Base privada do Null0x Chat

- Primeira versao funcional do aplicativo.
- Conversas P2P com identidade local e rotas onion.
- Interface inicial de chat, lista de conversas e fluxo basico de perfil.
- Persistencia local das mensagens no aparelho.
- Primeiros testes de envio, recebimento e conversa com a propria rota.

### v1.0.1 — Estabilidade, contatos e experiencia de uso

- Melhorias no gerenciamento de contatos e pedidos de conversa.
- Ajustes no estado de entrega das mensagens e sincronizacao local.
- Refinamento do fluxo de perfil, nome publico, emoji e identificacao da rota.
- Preparacao das preferencias de privacidade por conversa.
- Correcoes de comportamento ao alternar entre Wi-Fi, dados moveis e Tor.

### v1.0.2 — Mapa, ajustes e Null IA experimental

- Inclusao das telas de ajustes e organizacao das preferencias do app.
- Melhorias no mapa offline, dados territoriais e visualizacao de regioes.
- Primeira integracao experimental da Null IA local com modelos GGUF.
- Download/importacao de modelo local e preparacao do motor `llama.cpp`.
- Ajustes de versionamento automatico e metadados de atualizacao.

### v1.0.3 — Em breve

- Null IA mais leve, com prompt reduzido, memoria curta e limpeza imediata ao apagar conversa.
- Ferramentas locais para calculos, datas e conversoes usadas como contexto do modelo.
- Pos-processamento para preservar respostas numericas exatas sem substituir a resposta da IA.
- Otimizacoes no caminho nativo do `llama.cpp`, com contexto reutilizavel e limites de tempo mais coerentes.
- Refinos de privacidade para evitar vestigios locais quando o usuario limpa ou remove uma conversa.
