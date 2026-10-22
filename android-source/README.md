# JeaxLabs OS — ROM Simulator 1.2.0

Aplikasi Android yang mensimulasikan pengalaman antarmuka sistem JeaxLabs OS sambil pengembangan ROM/fimware berjalan. Target de teste inicial: Samsung Galaxy Tab A7 Lite LTE (SM-T225).

## Telas e recursos

- Tela de inicialização JeaxLabs e fluxo de boas-vindas/configuração inicial.
- Tela de bloqueio simulada com relógio, data, resumo de notificações e desbloqueio por toque/deslize.
- Tela inicial com papel de parede em degradê inspirado no layout de referência, widget de clima configurável, barra de pesquisa, ícones dos apps realmente instalados, indicadores de página e dock com favoritos detectados no aparelho.
- Gaveta de aplicativos com pesquisa e abertura de aplicativos instalados.
- Central de controle com atalhos para Wi-Fi, Bluetooth, tela, som, bateria e notificações; controle de brilho limitado à janela do app.
- Configurações JeaxLabs e acesso aos painéis reais do Android.
- Central de notificações opcional, mediante autorização explícita do Notification Access do Android.
- JeaxLabs Task Locker com PIN e integração ao Lock Task Mode do Android quando o app estiver provisionado como Device Owner.
- Loja consulta o catálogo de aplicativos remoto; canal de atualizações consulta metadados periodicamente.

## O que é simulado versus nativo

O APK simula a interface visual e a navegação do futuro sistema. Ele continua rodando por cima do Android instalado: não é uma ROM/firmware, não substitui a animação de boot original, não controla todos os componentes privilegiados da Samsung e não fornece ainda instalação criptográfica de \`.jxa\` nem atualização transacional de \`.jxu\`. O clima permite guardar o nome da cidade, mas não apresenta previsão real nesta versão. Botões de conexão abrem as configurações nativas porque um APK comum não pode ligar/desligar livremente todas as rádios no Android moderno.

O Device Owner/Task Locker precisa de provisionamento e deve ser testado no modelo de tablet e versão de Android específicos. O modo de bloqueio não deve ser tratado como segurança de produção até os testes em dispositivo real.

## Compilação pelo GitHub Actions

O workflow usa Java 17 e Gradle 8.9. Ele gera um APK de depuração e um ZIP contendo APK, fonte e instruções. O APK debug é para teste; para distribuição de produção ainda é necessário configurar assinatura de release segura.

Comando de provisionamento (quando o tablet estiver preparado e o ADB autorizado):

\`\`\`bat
adb shell dpm set-device-owner br.com.jeaxlabs.os/.JeaxDeviceAdminReceiver
adb shell dpm list-owners
\`\`\`

A primeira configuração do sistema precisa ser concluída uma vez; depois o lançamento do JeaxLabs pode mostrar a tela de bloqueio simulada para demonstrar o fluxo de boot/desbloqueio.
