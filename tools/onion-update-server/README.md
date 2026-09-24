# Servidor onion de atualizações

Este diretório contém um servidor HTTP estático sem dependências externas e uma ferramenta de publicação atômica. O servidor aceita somente `GET`/`HEAD`, não mantém access log, não lista diretórios e só escuta no loopback. O Tor publica a porta local como onion service.

## 1. Preparar o all-in-one

Instale Python 3 e Tor. Copie `server.py` para `/opt/nullchat-update-server/` e use `nullchat-update-server.service.example` como base para o serviço systemd. Crie o usuário e os diretórios com permissões mínimas:

```bash
sudo useradd --system --home /var/lib/nullchat-updates --shell /usr/sbin/nologin nullchat-update
sudo install -d -o nullchat-update -g nullchat-update -m 0755 /var/lib/nullchat-updates/public
sudo install -d -o root -g root -m 0755 /opt/nullchat-update-server
sudo install -o root -g root -m 0755 server.py /opt/nullchat-update-server/server.py
```

Copie as linhas de `torrc.example` para `/etc/tor/torrc`, reinicie o Tor e consulte o hostname:

```bash
sudo systemctl restart tor
sudo cat /var/lib/tor/nullchat_updates/hostname
```

Faça backup offline de `/var/lib/tor/nullchat_updates/`. Essa pasta contém a identidade do endereço onion.

## 2. Configurar o aplicativo

Nunca grave o hostname real diretamente no código. Compile usando uma propriedade Gradle ou variável de ambiente:

```bash
./gradlew assembleRelease -PappUpdateOnionHost=SEU_HOST_DE_56_CARACTERES.onion
```

ou:

```bash
export NULLCHAT_UPDATE_ONION_HOST=SEU_HOST_DE_56_CARACTERES.onion
./gradlew assembleRelease
```

O hostname passa a compor `BuildConfig.UPDATE_ONION_HOST`. O placeholder padrão não é um serviço real.

## 3. Publicar uma release

O APK precisa estar assinado com o mesmo certificado da instalação existente. Verifique-o antes de publicar:

```bash
apksigner verify --verbose --print-certs app-release.apk
```

Para compilar, incrementar a versão e publicar em um único comando:

```bash
export NULLCHAT_UPDATE_ONION_HOST=SEU_HOST_DE_56_CARACTERES.onion
export NULLCHAT_UPDATE_PUBLIC_DIR=/var/lib/nullchat-updates/public
tools/onion-update-server/release_and_publish.sh security "Correções de segurança."
```

Use `major`, `minor` ou `security` como primeiro argumento. O script usa o versionamento já existente no Gradle, lê o `version.properties` atualizado e publica o APK somente se o build de release terminar com sucesso.

Publique o APK e gere o manifesto em uma única operação:

```bash
python3 tools/onion-update-server/publish_release.py \
  --apk app-release.apk \
  --public-dir /var/lib/nullchat-updates/public \
  --onion-host SEU_HOST_DE_56_CARACTERES.onion \
  --version-code 1000003 \
  --version-name 1.0.3 \
  --release-notes "Correções e melhorias."
```

O APK é promovido primeiro e `update.json` por último. Ambos usam troca atômica, evitando manifestos que apontam para arquivos incompletos.

## 4. Comportamento no Android

Com atualização automática habilitada, o app:

1. consulta `update.json` exclusivamente pelo Tor;
2. compara o `versionCode`;
3. mostra o progresso em notificação;
4. limita o download a 512 MiB;
5. valida tamanho e SHA-256;
6. confere package, versionCode e certificado de assinatura do APK;
7. mostra a ação de instalação;
8. abre a permissão de fontes desconhecidas quando necessária.

O Android exige confirmação do usuário para instalar um APK em um aparelho comum. O servidor não recebe identificadores, cookies ou analytics; ainda assim, padrões de disponibilidade e volume podem ser observados na rede Tor.
