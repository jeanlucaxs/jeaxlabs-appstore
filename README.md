# JeaxLabs OS e App Store

Este repositório contém o APK do JeaxLabs OS, o catálogo de aplicativos e as automações de build e release. A versão Android é definida em `android-source/version.properties`.

## Publicação de aplicativos na Store

Cada app fica em uma pasta dentro de `packages/`:

```text
packages/
  identificador-do-app/
    app.json
    app.jxa
    01.png
    02.png
    ...
    25.png
```

O `app.json` tem o título, descrição, versão, desenvolvedor, categoria e o nome do pacote JXA. As imagens PNG são opcionais e a Store mostra apenas as que existem e podem ser carregadas. Veja o [modelo de publicação](packages/README.md).

O gerador interno **JeaxLabs JXA Studio** é distribuído separadamente à equipe de TI e não é armazenado nesta árvore pública. Não coloque chaves privadas, senhas ou ferramentas internas de assinatura neste repositório. O repositório precisa continuar público para que os tablets possam consultar o catálogo sem credenciais embutidas no APK.

## Política de versões

Toda versão numerada distribuída deve ser oficial. Artefatos de CI servem apenas para validação e não contam como lançamento.

- Política completa: [RELEASE_POLICY.md](RELEASE_POLICY.md)
- Histórico de alterações: [CHANGELOG.md](CHANGELOG.md)
- Versão e versionCode: [version.properties](android-source/version.properties)
- Metadados da release: [catalog.json](catalog.json)

Para publicar uma versão oficial, aumente a versão e o versionCode, atualize o changelog e o catálogo de release, configure os GitHub Actions Secrets e envie uma tag `vX.Y.Z` igual à versão central. A workflow valida, compila, assina e publica os artefatos.

## Limites técnicos

O JeaxLabs OS atual é um aplicativo launcher/kiosk executado sobre Android, não uma ROM/firmware independente. Ele pode mostrar sua tela de abertura depois que a Activity inicia, mas não substitui a animação nativa anterior ao início do Android. O botão físico de energia é controlado pelo sistema; o APK pode reagir ao retorno da tela quando continua ativo, mas não pode interceptar globalmente esse botão como uma ROM. Bloqueio gerenciado e instalação sem confirmação dependem do que Android e o firmware permitem. A validação final deve ser feita no tablet físico.
