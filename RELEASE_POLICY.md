# Política de versões oficiais — JeaxLabs OS

## Regra do projeto

Toda versão numerada publicada do JeaxLabs OS é uma versão oficial. Builds de CI são artefatos de validação, não versões distribuídas.

## Fonte única da versão

`android-source/version.properties` define a versão Android:
- `VERSION_NAME`: versão SemVer, por exemplo `1.6.0`.
- `VERSION_CODE`: inteiro Android maior que o de toda versão já distribuída.

Ao preparar uma versão, atualizar também o campo `release` em `catalog.json` e adicionar notas reais no `CHANGELOG.md`. A tag oficial deve corresponder exatamente ao nome, no formato `v<VERSION_NAME>`.

## Publicação oficial

A workflow `.github/workflows/official-release.yml`:
1. Confere tag, versão, catálogo e changelog.
2. Exige a chave permanente de assinatura Android guardada em GitHub Actions Secrets.
3. Compila o APK, executa os testes automatizados e verifica sua assinatura.
4. Publica APK, checksums, código Android e notas de release.
5. Gera o pacote de atualização `.jxu` no runner da release e sincroniza o manifesto em `jeaxlabs-os-updates`.

Não crie uma chave Android diferente a cada release. Guarde cópias de segurança do keystore e suas senhas fora do repositório.

## Secrets necessários

Configure em **Settings → Secrets and variables → Actions**:
- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`
- `UPDATES_REPO_TOKEN`
- `JXU_PUBLISHER_KEY_BASE64`
- `JXU_PUBLISHER_KEY_PASSWORD`
- `JXU_PACKAGE_PASSWORD`

Nenhuma chave privada deve ser commitada no GitHub. O gerador interno de aplicações JXA é distribuído separadamente à equipe de TI e não pertence aos artefatos públicos. O workflow não inclui utilitários Python nos arquivos publicados.

## Requisitos para cada versão

- Aumentar `VERSION_CODE`; nunca reutilizar um código já distribuído.
- Escrever notas reais no changelog e não declarar testes físicos que não foram feitos.
- Publicar o APK assinado, checksums, código-fonte e notas.
- Manter o manifesto de atualizações sincronizado.

O JeaxLabs OS continua sendo um launcher sobre Android. Uma release oficial do aplicativo não é uma ROM/firmware e não substitui teste no dispositivo físico.
