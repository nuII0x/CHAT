# Null0x Market Server

Fundação em C++17 para o servidor onion do **Mercado**. O processo aceita apenas
`127.0.0.1`; a exposição externa deve ser feita pelo hidden service descrito em
`torrc.example`.

## Estado de segurança

Este estágio é deliberadamente **não custodial e sem negociação real**. Os campos
`tradingEnabled` e `custodyEnabled` retornam `false`. Não deposite fundos neste
servidor. Essa trava evita que uma interface inicial seja confundida com uma
corretora auditada.

Já estão disponíveis:

- health check e snapshot simulado de BTC/USDT e XMR/USDT;
- recebimento durável de pacotes opacos de mensagens;
- limite explícito de requisição;
- binding obrigatório em loopback;
- configuração Tor e unidade systemd endurecida.
- ledger de partidas dobradas por ativo com journal append-only;
- reserva e liberação de saldo;
- motor preço/tempo, execução parcial e cancelamento;
- taxa taker de demonstração de 0,20% creditada em `platform_revenue`;
- rejeição de overflow, saldo negativo e transações desbalanceadas.
- registro custodial persistente vinculado ao hash da identidade Ed25519;
- restauração da mesma conta e dos mesmos endereços após reinstalação;
- rejeição de troca silenciosa de um endereço já registrado.

## Identidade, senha e recuperação

A senha nunca é enviada ao servidor. No Android ela desbloqueia a seed cifrada e,
junto da mnemônica, restaura a mesma identidade Ed25519 e a mesma rota onion. O
servidor identifica a conta custodial pelo hash dessa chave pública.

```text
mnemônica + senha (somente no aparelho)
              │
              ▼
       identidade Ed25519
              │ requisição assinada
              ▼
acct_<hash da chave pública> ── BTC/XMR/USDT deposit addresses
```

Assim, restaurar a identidade recupera o acesso à mesma conta centralizada. As
chaves privadas on-chain continuam exclusivamente no servidor custodial e não são
derivadas da senha do usuário.

## Compilar e testar

```bash
make -C tools/market-server
make -C tools/market-server test
```

## Rodar localmente

```bash
make -C tools/market-server
mkdir -p "$PWD/market-data"
tools/market-server/null-market-server \
  --host 127.0.0.1 \
  --port 8899 \
  --data "$PWD/market-data"
```

Em outro terminal:

```bash
curl http://127.0.0.1:8899/v1/health
curl http://127.0.0.1:8899/v1/market/snapshot
```

## Publicar somente pelo Tor

1. Instale o Tor no computador que executará o servidor.
2. Copie as linhas de `torrc.example` para a configuração do Tor, ajustando
   apenas `DataDirectory` e `HiddenServiceDir` para diretórios pertencentes ao
   usuário do serviço.
3. Reinicie o Tor.
4. Leia o hostname criado em `HiddenServiceDir/hostname`.
5. Teste por outro processo Tor:

```bash
curl --socks5-hostname 127.0.0.1:9050 http://SEU_HOST.onion/v1/health
```

Não altere o bind do servidor para `0.0.0.0`. O binário rejeita essa opção.

## Por que depósitos reais ainda estão bloqueados

O ledger e o matching engine não criam endereços blockchain. Para aceitar fundos
sem perdê-los ainda são necessários adaptadores autenticados para Bitcoin Core,
Monero Wallet RPC e para uma rede USDT definida, além de confirmações, reorgs,
idempotência, reconciliação, hot/cold wallets e aprovação de retiradas.

É seguro usar esta versão para desenvolvimento e dados simulados. Não é seguro
publicar endereços de depósito nem alterar manualmente `custodyEnabled`.

## Próximos módulos, antes de habilitar fundos

1. Autenticação Ed25519 versionada, nonce e proteção contra replay.
2. Banco transacional e ledger de partidas dobradas.
3. Motor de ordens preço/tempo com valores inteiros.
4. Adaptadores isolados para Bitcoin Core, Monero Wallet RPC e a rede USDT escolhida.
5. Hot wallet limitada, cold storage, aprovação de retiradas e reconciliação.
6. Fila de envelopes end-to-end encrypted com destinatário, TTL, pull e ACK.
7. Auditoria externa e operação prolongada em testnet/stagenet.

O relay de pacotes não deve interpretar conteúdo de mensagens. O servidor custodial
e o relay podem compartilhar o processo operacional, mas usam armazenamento,
autorização, limites e trilhas de auditoria separados.
