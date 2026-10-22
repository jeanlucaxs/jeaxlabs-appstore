# Política de versões oficiais — JeaxLabs OS

## Regra do projeto

Toda versão numerada publicada do JeaxLabs OS é uma versão oficial. Não publicar versões de produto com nomes como beta, preview, experimental ou release candidate. Builds usados para validar commits no GitHub Actions são apenas artefatos internos de CI e não são versões distribuídas do produto.

## Fonte única da versão

O arquivo `android-source/version.properties` é a fonte da versão Android:
- `VERSION_NAME`: versão SemVer, por exemplo `1.4.1`.
- `VERSION_CODE`: inteiro Android estritamente maior que o de toda versão já distribuída.

Ao preparar uma nova versão, atualizar no mesmo commit `android-source/version.properties`, o campo `release` de `catalog.json` e uma entrada com notas em `CHANGELOG.md`. A tag oficial deve corresponder exatamente ao nome, no formato `v<VERSION_NAME>`.

## Publicação oficial

A publicação oficial é feita pela workflow `.github/workflows/official-release.yml` ao enviar a tag correspondente. Ela:
1. Confere a tag, a versão, o catálogo e o changelog.
2. Exige a chave permanente de assinatura Android armazenada em GitHub Actions Secrets.
3. Compila e testa o build de release, verifica a assinatura e calcula SHA-256.
4. Publica o APK assinado, o código-fonte e os utilitários Python em uma GitHub Release.
5. Aciona a atualização do manifesto no repositório `jeaxlabs-os-updates`.

Não criar uma nova chave de assinatura para cada versão. Guardar uma cópia de segurança segura do keystore e manter sua senha fora do repositório.

## Secrets necessários no repositório principal

Configurar em **Settings → Secrets and variables → Actions**:
- `ANDROID_KEYSTORE_BASE64`: keystore de produção convertido para Base64.
- `ANDROID_KEYSTORE_PASSWORD`: senha do keystore.
- `ANDROID_KEY_ALIAS`: alias da chave.
- `ANDROID_KEY_PASSWORD`: senha da chave.
- `UPDATES_REPO_TOKEN`: token com acesso de escrita ao repositório `jeaxlabs-os-updates`, usado apenas para disparar a sincronização do manifesto.

A keystore deve ser criada e armazenada de forma segura antes da primeira publicação oficial assinada. Sem esses secrets, o workflow bloqueia a publicação em vez de marcar um APK debug como oficial.

## Requisitos para cada versão

- Aumentar `VERSION_CODE`; nunca reutilizar um código de versão já distribuído.
- Escrever notas reais no changelog; não declarar testes físicos que não foram realizados.
- Publicar APK assinado, SHA-256, fonte e as duas ferramentas Python.
- Manter os metadados de atualização sincronizados.
- Distinguir claramente um build de CI de uma versão oficial distribuída.

O JeaxLabs OS atual continua sendo um aplicativo/launcher sobre Android. A publicação como versão oficial do aplicativo não significa que seja firmware/ROM nem substitui a validação em um dispositivo físico.
