# JeaxLabs App Store

Repositório inicial da loja de aplicativos do JeaxLabs OS v1.0.0.

## Objetivo
- Publicar o catálogo de aplicativos disponíveis na loja.
- Distribuir pacotes JeaxLabs no formato `.jxa`.
- Fornecer metadados como identificador, nome, versão, descrição e URL do pacote.
- Permitir que o cliente valide integridade e assinatura antes de instalar.

## Arquivos
- `catalog.json`: catálogo inicial, atualmente sem aplicativos publicados.
- `packages/`: diretório reservado para pacotes publicados.

## Segurança
Não publique chaves privadas, senhas, tokens ou certificados de assinatura privados. Um certificado incluído dentro do próprio pacote não basta, sozinho, para provar a autenticidade do pacote; o cliente deve validar uma assinatura confiável e a integridade do conteúdo.

## Estado
Infraestrutura inicial. O formato final do pacote `.jxa` e a integração com o aplicativo Android ainda estão em desenvolvimento.
