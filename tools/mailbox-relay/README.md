# Primalis Mailbox Relay

Relay descartavel de caixa postal para envelopes criptografados.

Ele nao descriptografa mensagens, nao conhece contatos e nao substitui o Tor/P2P do app. A funcao dele e guardar envelopes opacos por `recipientPublicKeyHash` ate o destinatario buscar e confirmar recebimento.

## Build

```bash
make -C tools/mailbox-relay
```

## Execucao

```bash
tools/mailbox-relay/mailbox-relay --host 127.0.0.1 --port 8787 --data tools/mailbox-relay/relay-data
```

Use `--host 0.0.0.0` apenas quando for expor o relay em uma rede confiavel ou por um servico onion.

## Endpoints

### `GET /v1/relay/status`

Retorna dados publicos do relay:

```json
{
  "ok": true,
  "relayId": "...",
  "startedAt": 1784300000000,
  "uptimeSeconds": 120,
  "storedEnvelopes": 3,
  "acceptedEnvelopes": 10,
  "deliveredEnvelopes": 7,
  "trustHint": 120
}
```

Clientes podem preferir relays com maior `uptimeSeconds`/`trustHint`.

### `POST /v1/relay/envelopes`

Armazena um envelope criptografado.

Aceita um objeto envelope diretamente ou `{ "envelope": { ... } }`.

Campos minimos:

```json
{
  "messageId": "uuid-ou-hash",
  "recipientPublicKeyHash": "hash-do-destinatario",
  "ciphertext": "base64",
  "nonce": "base64",
  "timestamp": 1784300000000,
  "ttl": 86400000,
  "proof": "assinatura"
}
```

O relay valida tamanho, TTL e campos obrigatorios, mas nao valida assinatura criptografica. Essa validacao fica no cliente/app.

### `POST /v1/relay/pull`

Busca envelopes de um destinatario:

```json
{
  "recipientPublicKeyHash": "hash-do-destinatario",
  "limit": 50
}
```

Resposta:

```json
{
  "ok": true,
  "messages": [
    { "messageId": "...", "recipientPublicKeyHash": "...", "ciphertext": "...", "nonce": "..." }
  ]
}
```

### `POST /v1/relay/ack`

Remove um envelope apos o destinatario confirmar persistencia local:

```json
{
  "messageId": "...",
  "recipientPublicKeyHash": "hash-do-destinatario"
}
```

## Modelo de confianca

- O relay so guarda blobs criptografados.
- O relay pode apagar mensagens, ficar offline ou mentir sobre uptime.
- A confianca inicial e operacional: relays que ficam mais tempo online sao melhores candidatos.
- A seguranca de conteudo vem do envelope assinado/criptografado do app, nao deste processo.

## Uso futuro com Tor

Rode este binario na porta local e publique a porta por um onion service. O app pode futuramente descobrir relays, consultar `/status`, escolher os mais estaveis e replicar envelopes para alguns deles.
