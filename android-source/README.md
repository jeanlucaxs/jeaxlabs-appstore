# JeaxLabs OS — documentação técnica

A versão Android é definida em `version.properties`. O tablet alvo inicial é o Samsung Galaxy Tab A7 Lite (SM-T225).

## Experiência do launcher

- Ao iniciar a Activity, aparece uma tela preta com a logo central por 15 segundos, sem contador.
- A lista de aplicativos contém apenas apps instalados com sucesso por um pacote JXA autorizado pela Store.
- A navegação interna usa três controles: recentes (quadrado), início (círculo) e voltar (triângulo).
- Após a configuração inicial, apagar e acender a tela pode levar à tela de bloqueio interna se a Activity ainda estiver ativa.
- O assistente não permite concluir a configuração até existir uma conexão Wi-Fi com internet validada.
- A Loja lê uma pasta por app em `packages/<id>/`, contendo `app.json`, o pacote JXA e imagens opcionais `01.png` a `25.png`.
- Os metadados da loja vêm de `app.json`; imagens ausentes ou que falharem ao carregar não aparecem como placeholders.
- JXA usa assinatura Ed25519 e AES-GCM. A instalação não pede senha por app; os pacotes precisam corresponder ao certificado autorizado pelo APK.

## Build

A CI executa os testes disponíveis e cria um APK debug identificado como artefato CI. Uma release oficial exige uma tag `vX.Y.Z`, versionCode incrementado e os GitHub Actions Secrets descritos no [RELEASE_POLICY.md](../RELEASE_POLICY.md). A chave de assinatura Android de produção deve permanecer fora do repositório.

## Limites técnicos

Este projeto é um aplicativo Android launcher/kiosk, não uma ROM/firmware independente. A tela preta de 15 segundos é uma tela do app depois que Android inicia a Activity; não altera a animação nativa de boot antes do Android. Apps comuns não podem capturar globalmente o botão físico de energia, substituir o kernel/recovery ou controlar todas as políticas do sistema. A barra personalizada só existe enquanto o launcher está visível, e o Android pode exigir confirmação de instalação ou de conexões. Funcionalidades de Device Owner/Lock Task dependem de provisionamento válido pelo Android e de compatibilidade do firmware.

Wi-Fi moderno usa APIs do sistema e o Android pode exibir o próprio painel ou pedir confirmação para conectar. A tela de bloqueio do app não substitui uma credencial segura do Android. Teste as transições, instalação JXA, wake/sleep e navegação no tablet antes de afirmar que a experiência funciona como firmware.
