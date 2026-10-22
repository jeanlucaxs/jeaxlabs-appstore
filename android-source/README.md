# JeaxLabs OS — versão de interface 1.1.0

Este repositório contém o aplicativo Android JeaxLabs OS, com uma interface de launcher e telas de sistema para tablets Android.

## O que esta versão inclui

- Tela de inicialização do aplicativo e fluxo de boas-vindas/configuração inicial.
- Tela inicial com relógio, atalhos e grade de aplicativos instalados.
- Pesquisa de aplicativos e abertura dos apps disponíveis no dispositivo.
- Central de controle e configurações que encaminham para os painéis oficiais do Android.
- Central de notificações que usa o acesso especial opcional do Android, após autorização explícita do usuário.
- Integração inicial com o catálogo remoto em \`catalog.json\`.
- Consulta periódica de metadados do canal de atualizações, no máximo uma vez a cada 24 horas quando o aplicativo é aberto.
- Tela JeaxLabs Task Locker com PIN e integração ao Android Lock Task Mode quando o aplicativo estiver provisionado como Device Owner.
- Pipeline GitHub Actions para compilar APK e gerar um ZIP com APK e código-fonte.

## Limites importantes

Este projeto ainda é um aplicativo de sistema/launcher, não uma ROM nem uma distribuição Android independente. Um APK normal não substitui a animação de boot do firmware, a tela de recuperação, as Configurações privilegiadas do fabricante ou o painel nativo completo. A tela de inicialização do JeaxLabs só aparece quando o Android inicia este aplicativo.

A loja já consulta o catálogo remoto, mas o catálogo atual está vazio. A instalação de pacotes criptografados \`.jxa\`, a verificação criptográfica final de assinaturas e a instalação transacional de atualizações \`.jxu\` ainda precisam de um protocolo final implementado e testado antes de serem anunciadas como prontas.

## Construir

O GitHub Actions executa \`gradle assembleDebug\` usando Java 17 e Gradle 8.9. O APK gerado é de depuração, não é assinado para uma publicação de produção.

Para provisionar Device Owner, o tablet precisa permitir o provisionamento da aplicação e estar nas condições exigidas pelo Android, geralmente após uma restauração de fábrica e antes de adicionar contas:
\`\`\`bat
adb shell dpm set-device-owner br.com.jeaxlabs.os/.JeaxDeviceAdminReceiver
\`\`\`

Confirme com:
\`\`\`bat
adb shell dpm list-owners
\`\`\`

O modo bloqueado deve ser testado no modelo e versão específicos do Android antes de uso real.
