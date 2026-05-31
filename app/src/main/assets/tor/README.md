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
