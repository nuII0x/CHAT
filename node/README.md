# NullChat node

`node` e um no de teste para rodar no Linux. Ele continua expondo os endpoints HTTP de apoio para envelopes, mas agora tambem funciona como servidor e cliente de chat compativel com o NullChat mobile.

O modo chat usa texto puro no terminal:

- recebe pedido de contato e mostra `Aceitar? [s/N]`
- aceita com `s` ou `sim`
- entra no chat automaticamente depois do aceite
- envia mensagens para o contato ativo digitando texto sem `/`
- responde ACKs para mensagens recebidas do NullChat
- envia mensagens via Tor SOCKS para outras rotas onion

## Build

```bash
cmake -S node -B node/build
cmake --build node/build
```

O binario gerado se chama:

```bash
./node/build/node
```

## Tor

Use `node/config/torrc.example` como base. Ele expoe:

- `80 -> 127.0.0.1:5080` para HTTP do node
- `5000 -> 127.0.0.1:5000` para chat NullChat

Depois que o Tor criar a onion, leia:

```bash
cat node/data/hidden_service/hostname
```

## Execucao

Com rota local explicita:

```bash
NULLCHAT_NODE_ROUTE="onion:SEU_HOST.onion:5000" \
NULLCHAT_NODE_PORT=5080 \
NULLCHAT_CHAT_PORT=5000 \
NULLCHAT_SOCKS_PORT=9050 \
NULLCHAT_NODE_DATA=node/data \
./node/build/node
```

Ou deixando o node ler o arquivo `hostname`:

```bash
NULLCHAT_NODE_HOSTNAME_FILE=node/data/hidden_service/hostname \
./node/build/node
```

## CLI

```text
/route                 mostra a rota local configurada
/add <rota>            envia pedido de contato
/to <rota>             troca o chat ativo
/accept <rota>         aceita contato pendente
/contacts              lista contatos
/quit                  encerra
```

Texto sem `/` envia mensagem para o chat ativo.

## Endpoints HTTP mantidos

- `GET /health`
- `POST /v1/p2p`
- `POST /v1/envelopes`
- `POST /v1/pull`
- `POST /v1/ack`

Esses endpoints continuam servindo para testes de replica/armazenamento temporario de envelopes.

## Rota Tor de baixa latencia

O node tambem pode funcionar como relay HTTP acessivel pela rede Tor. As mensagens continuam trafegando como pacotes criptografados do NullChat, e o acesso externo acontece pela onion do serviço, nao por IP local.

As rotas dessa camada exigem requests assinadas pela identidade do NullChat, entao chamadas sem a assinatura do app sao rejeitadas.

Endpoints:

- `POST /v1/fast/send`
- `POST /v1/fast/pull`

Para o Android acessar via Tor, o relay precisa expor uma onion service. O Tor do computador deve mapear a onion para a porta HTTP do node.

```bash
tor -f node/config/torrc.example
```

Depois que o `hostname` aparecer em `node/data/hidden_service/hostname`, o node anuncia automaticamente a onion do relay.

No app Android, em `Ajustes > Conexão > Rota Tor autenticada`, ative a opção. O app vai aceitar URLs onion e falar com elas via Tor SOCKS local.

```text
http://SEU_HASH.onion
```

Se o campo ficar vazio ou desligado, o app usa somente o transporte onion/Tor padrão.

## Rodar permanente no Linux

Para deixar o node sempre ligado e reiniciar sozinho, use o service de usuario:

```bash
./node/systemd/install-nullchat-node-service.sh
```

Isso instala o serviço em `systemd --user`, liga no boot da sessao e configura automaticamente:

- `NULLCHAT_NODE_HOST=0.0.0.0`
- `NULLCHAT_NODE_PORT=5080`
- `NULLCHAT_CHAT_PORT=5000`
- `NULLCHAT_NODE_DATA=~/.local/share/nullchat-node`
- `NULLCHAT_RELAY_HOSTNAME_FILE=.../node/data/hidden_service/hostname`

Se voce quiser usar a onion no relay rapido, inicie tambem o Tor com `node/config/torrc.example` ou aponte `NULLCHAT_RELAY_HOSTNAME_FILE` para o `hostname` do seu hidden service.

Se quiser acompanhar:

```bash
journalctl --user -u nullchat-node.service -f
```
