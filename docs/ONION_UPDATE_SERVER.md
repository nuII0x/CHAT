# Atualizações via Onion

O app procura um `update.json` em um endereço `.onion` e baixa o APK pelo Tor. Não use CDN pública para o APK nem para o JSON.

## Estrutura do diretório

```text
updates/
  update.json
  NullChat-1.2.3.apk
```

Exemplo de `update.json`:

```json
{
  "versionCode": 123,
  "versionName": "1.2.3",
  "apkUrl": "http://SEU_ENDERECO_ONION.onion/NullChat-1.2.3.apk",
  "releaseNotes": "Correções e melhorias."
}
```

O `versionCode` precisa ser maior que o `versionCode` instalado no aparelho.

## Servir no PC

Instale Tor e Nginx:

```bash
sudo apt update
sudo apt install tor nginx
```

Crie a pasta pública:

```bash
sudo mkdir -p /var/www/nullchat-updates
sudo cp update.json NullChat-1.2.3.apk /var/www/nullchat-updates/
sudo chown -R www-data:www-data /var/www/nullchat-updates
```

Configure o Nginx local em `/etc/nginx/sites-available/nullchat-updates`:

```nginx
server {
    listen 127.0.0.1:8088;
    server_name localhost;
    root /var/www/nullchat-updates;
    autoindex off;

    location / {
        try_files $uri =404;
    }
}
```

Ative:

```bash
sudo ln -s /etc/nginx/sites-available/nullchat-updates /etc/nginx/sites-enabled/nullchat-updates
sudo nginx -t
sudo systemctl reload nginx
```

Configure o Onion Service em `/etc/tor/torrc`:

```text
HiddenServiceDir /var/lib/tor/nullchat_updates/
HiddenServiceVersion 3
HiddenServicePort 80 127.0.0.1:8088
```

Reinicie o Tor:

```bash
sudo systemctl restart tor
sudo cat /var/lib/tor/nullchat_updates/hostname
```

O comando acima mostra o endereço `.onion`. Use esse endereço no `apkUrl` do `update.json` e no `APP_UPDATE_MANIFEST_URL` do app.

## Publicar nova versão

1. Gere o APK assinado.
2. Copie o APK para `/var/www/nullchat-updates/`.
3. Atualize `update.json` com `versionCode`, `versionName`, `apkUrl` e `releaseNotes`.
4. O app vai checar silenciosamente quando a atualização automática estiver ativa.
