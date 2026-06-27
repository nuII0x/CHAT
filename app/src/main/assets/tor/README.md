Coloque o binario do Tor por ABI nestes caminhos:

- tor/arm64-v8a/tor
- tor/armeabi-v7a/tor
- tor/x86_64/tor

Regras:
- O nome do arquivo deve ser exatamente `tor`.
- O binario deve ser executavel no Android para a ABI correspondente.
- No primeiro start, o app copia automaticamente para:
  `files/tor/tor`
  e tenta iniciar o processo Tor.

Sem o binario, o status no app mostra erro de Tor ausente.

LEGAL NOTICE:
This application includes or may distribute the Tor binary.

Tor is free software developed by The Tor Project, Inc. and contributors.
Tor is distributed under its own license.

This application is not affiliated with, endorsed by, or sponsored by The Tor Project.
