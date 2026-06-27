Null0x-Chat — Melhorias e Correções Pendentes

Este arquivo serve para anotar ideias, melhorias, correções e ajustes futuros do aplicativo.

---

✅ Prioridade Alta

- [x] Mostrar "aguardando rota..." dentro do chat que não tem estado explicitado como "disponível" ou "conectado", a regra é, alcançou sinal de vida da rota? mostra "conectado", ainda não? mostra "aguardando rota..." se o usuário está dentro do aplicativo, com atividades na rede. mostra "disponível".
- [x] Revisar estabilidade da conexão Tor ao trocar de rede.
- [x] Melhorar reconexão automática quando o app perder internet.
- [x] Garantir que mensagens não sejam duplicadas.
- [x] Melhorar lógica de adicionar/remover contatos.
- [x] Corrigir estados visuais confusos, como contato “adicionado” sem aparecer salvo.

---

🛠 Correções Pendentes

- [x] Verificar comportamento do BottomDock quando o teclado abre, ele não pode seguir o teclado, tem que ficar fixo embaixo, onde ele reside.
- [x] Verificar comportamento do BottomDock quando o teclado abre, ele não pode seguir o teclado, tem que ficar fixo embaixo, onde ele reside.
- [x] Revisar permissões no AndroidManifest.xml.
- [x] Conferir ícones em mipmap e drawable.
- [x] Remover arquivos/imagens não usados.
- [ ] Testar APK em outro aparelho Android.

---

✨ Melhorias de Interface

- [x] Mudar a cor de fundo do SplashScreen para ficar de acordo com o tema do sistema. Este SplashScreen deve ficar ali até o app terminar de carregar a tela inicial, para não travar no toque até a abertura do app. O toque já abre imediatamente a SplashScreen.
- [x] A tela inicial, onde há Chats, quando o usuário pressionar e segurar, vai aparecer igual se comporta no Contatos, com a mesma ação, neste caso, irá aparecer lá na barra de título a lixeira, junto com 3 pontos que abre uma lista de opções extras, estas opções serão inicialmente: "Limpar chats, Selecionar tudo", depois com a evolução do app adicionamos mais opções. Esta ação em qualquer lugar do app mostrará um destaque nos itens da lista selecionados na cor de acordo com o tema.
- [x] Centralizar textos informativos.
- [x] Harmonizar botões de adicionar, remover e excluir.
- [x] Melhorar mensagens de status do app.
- [x] Criar feedback visual para conexão ativa/inativa.
- [x] Melhorar tela de contatos vazia.
- [x] dentro do chat, na barra de título em 3 pontos o menu que se abre tem "Bloquear", essa opção deve ir para configurações, deve ser um botão que segue o tema, quando bloqueado vira "Desbloquear" como está atualmente.
- [x] Colocar legendas embaixo de cada item do dock para ficar fácil saber o nome de cada aba e diferenciá-las.

---

🔐 Segurança e Privacidade

- [x] Implementar frase-passe de 12 palavras para restaurar identidade.
- [x] Implementar identidade criptográfica permanente derivada de chave pública.
- [x] Permitir backup e restauração do endereço onion mantendo o mesmo endereço após reinstalação.
- [x] Proteger token de rota privada.
- [x] Evitar exposição desnecessária do endereço onion.
- [x] Revisar armazenamento local de mensagens.
- [x] Impedir envio para rotas não autorizadas.

---

📦 Publicação

- [ ] Criar release no GitHub.
- [ ] Gerar APK assinado.
- [ ] Adicionar sistema de atualização via GitHub Releases.
- [x] Escrever instruções de instalação.
- [x] Definir versão atual do app.
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

`major` aumenta `x` e zera `y` e `z`, `minor` aumenta `y` e zera `z`, e `security` aumenta apenas `z`.

---

🧪 Testes

- [ ] Testar chat comigo mesmo.
- [ ] Testar envio com outro aparelho.
- [ ] Testar app sem internet.
- [ ] Testar mudança de Wi-Fi para dados móveis.
- [ ] Testar reinício do celular.

---

💡 Ideias Futuras

- [x] Foreground Service + Tor reconectável + fila local + ACK.
- [x] Sistema descentralizado de armazenamento temporário.
- [x] Mensagens offline distribuídas.
- [x] Sons de notificação personalizados.
- [x] Integração com avatares.
- [x] Tema visual mais refinado.

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
