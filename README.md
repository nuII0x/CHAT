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

Versões

v1.0

- Primeira fase de desenvolvimento.
- Chat básico.
- Testes com rota onion.
- Ajustes iniciais de interface.
