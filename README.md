# NullChat — Melhorias e Correções Pendentes

Este arquivo serve para anotar ideias, melhorias, correções e ajustes futuros do aplicativo.

---

## ✅ Prioridade Alta
- [ ] Mostrar "aguardando rota..." dentro do chat que não tem estado explicitado como "disponível" ou "conectado", a regra é, alcançou sinal de vida da rota? mostra "conectado", ainda não? mostra "aguardando rota..." se o usuário está dentro do aplicativo, com atividades na rede. mostra "disponível".
- [ ] Revisar estabilidade da conexão Tor ao trocar de rede.
- [ ] Melhorar reconexão automática quando o app perder internet.
- [ ] Garantir que mensagens não sejam duplicadas.
- [ ] Melhorar lógica de adicionar/remover contatos.
- [ ] Corrigir estados visuais confusos, como contato “adicionado” sem aparecer salvo.

---

## 🛠 Correções Pendentes

- [ ] Verificar comportamento do BottomDock quando o teclado abre, ele não pode seguir o teclado, tem que ficar fixo embaixo, onde ele reside.
- [ ] Revisar permissões no `AndroidManifest.xml`.
- [ ] Conferir ícones em `mipmap` e `drawable`.
- [ ] Remover arquivos/imagens não usados.
- [ ] Testar APK em outro aparelho Android.

---

## ✨ Melhorias de Interface
- [ ] Mudar a cor de fundo do SplashScreen para ficar de acordo com o tema do sistema. Este SplashScreen deve ficar alí até o app terminar de carregar a tela inicial, para não travar no toque até a abertura do app. O toque já abre imediatamente a SplashScreen.
- [ ] A tela inicial, onde há Chats, quando o usuário pressionar e segurar, vai aparecer igual se comporta no Contatos, com a mesma ação, neste caso, irá aparecer lá na barra de título a lixeira, junto com 3 pontos que abre uma lista de opções extras, estas opções serão inicialmente: "Limpar chats, Selecionar tudo", depois com a evolução do app adicionamos mais opções. Esta ação em qualquer lugar do app mostrará um destaque nos itens da lista selecionados na cor de acordo com o tema.
- [ ] Centralizar textos informativos.
- [ ] Harmonizar botões de adicionar, remover e excluir.
- [ ] Melhorar mensagens de status do app.
- [ ] Criar feedback visual para conexão ativa/inativa.
- [ ] Melhorar tela de contatos vazia.
- [ ] dentro do chat, na barra de título em 3 pontos o menu que se abre tem "Bloquear", essa opção deve ir para configurações, deve ser um botão que segue o tema, quando bloqueado vira "Desbloquear" como está atualmente. 

---

## 🔐 Segurança e Privacidade

- [ ] Implementar frase-passe de 12 palavras para restaurar identidade.
- [ ] Proteger token de rota privada.
- [ ] Evitar exposição desnecessária do endereço onion.
- [ ] Revisar armazenamento local de mensagens.
- [ ] Impedir envio para rotas não autorizadas.

---

## 📦 Publicação

- [ ] Criar release no GitHub.
- [ ] Gerar APK assinado.
- [ ] Adicionar sistema de atualização via GitHub Releases.
- [ ] Escrever instruções de instalação.
- [ ] Definir versão atual do app.

---

## 🧪 Testes

- [ ] Testar chat comigo mesmo.
- [ ] Testar envio com outro aparelho.
- [ ] Testar app sem internet.
- [ ] Testar mudança de Wi-Fi para dados móveis.
- [ ] Testar reinício do celular.

---

## 💡 Ideias Futuras

- [ ] Sistema descentralizado de armazenamento temporário.
- [ ] Mensagens offline distribuídas.
- [ ] Sons de notificação personalizados.
- [ ] Integração com avatares.
- [ ] Tema visual mais refinado.

---

## 📝 Anotações Rápidas

- 
- 
- 

---

## Versões

### v0.1
- Primeira fase de desenvolvimento.
- Chat básico.
- Testes com rota onion.
- Ajustes iniciais de interface.
