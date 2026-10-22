# JeaxLabs OS e App Store

Este repositório contém o aplicativo JeaxLabs OS, o catálogo da loja e a automação de compilação/publicação. A versão Android única é definida por `android-source/version.properties`.

## Política de versões

Toda versão numerada distribuída do JeaxLabs OS deve ser oficial: sem publicações beta, preview ou release candidate. Artefatos de CI podem ser gerados para validação, mas são identificados como CI e não contam como versões distribuídas.

- Política completa: [RELEASE_POLICY.md](RELEASE_POLICY.md)
- Histórico de alterações: [CHANGELOG.md](CHANGELOG.md)
- Versão e versionCode: [version.properties](android-source/version.properties)
- Catálogo público: [catalog.json](catalog.json)

## Como uma versão oficial é publicada

1. Atualize `VERSION_NAME` e aumente `VERSION_CODE` em `version.properties`.
2. Atualize `catalog.json` e adicione notas reais para a versão em `CHANGELOG.md`.
3. Configure os secrets descritos em `RELEASE_POLICY.md`, inclusive a chave de assinatura Android persistente.
4. Faça commit dessas alterações e envie uma tag `vX.Y.Z` que corresponda exatamente a `VERSION_NAME`.
5. A workflow `Official Release` valida, compila, assina, verifica a APK, publica os artefatos na página Releases e sincroniza o manifesto do repositório `jeaxlabs-os-updates`.

Se os requisitos de assinatura ou de metadados não forem cumpridos, a publicação oficial é bloqueada. Não se deve chamar uma APK debug de lançamento oficial.

## Loja e pacotes

- `tools/JeaxLabsAppPacker.py`: cria pacotes `.jxa`.
- O empacotador de atualizações `tools/JeaxLabsUpdatePacker.py` fica no repositório `jeaxlabs-os-updates` e é incluído nos pacotes de build.
- `catalog.json`: catálogo de aplicativos publicados; o catálogo permanece vazio até que pacotes reais sejam integrados.

## Limites técnicos conhecidos

O APK atual oferece uma experiência de launcher/kiosk sobre Android; não é uma ROM ou firmware independente. O fluxo de instalação/atualização dos formatos `.jxa` e `.jxu` ainda não está implementado dentro do APK. O comportamento de kiosk, bloqueio e políticas Device Owner precisa ser validado em dispositivo físico antes de declarar essa validação concluída.
