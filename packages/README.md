# JeaxLabs Store — publicação de aplicativos

Cada aplicativo precisa ficar em sua própria pasta dentro de `packages/`. A Store só mostra uma pasta quando encontra `app.json` e um arquivo `.jxa` válido.

## Estrutura

```text
packages/
  meu-aplicativo/
    app.json
    app.jxa
    01.png
    02.png
    ...
    25.png
```

As imagens são opcionais. Use PNGs opacos (sem transparência) nomeados de `01.png` até `25.png`. A Store escaneia cada pasta e mostra somente os arquivos de imagem que realmente existem e conseguem ser carregados; sem imagens, a galeria fica oculta.

## Exemplo de app.json

```json
{
  "id": "meu-aplicativo",
  "title": "Meu Aplicativo",
  "description": "Descrição do aplicativo exibida na Loja JeaxLabs.",
  "version": "1.0.0",
  "developer": "JeaxLabs",
  "category": "Produtividade",
  "package_file": "app.jxa",
  "screenshots": ["01.png", "02.png"]
}
```

O campo `package_file` deve corresponder exatamente ao nome do arquivo JXA naquela pasta. `screenshots` é informativo; a Store confere a presença real de cada PNG na pasta antes de exibi-lo.

## Publicar

1. Crie uma pasta para o app em `packages/`.
2. Envie o `.jxa`, o `app.json` e os PNGs disponíveis.
3. Faça Commit changes. A Store atualizada lê o catálogo automaticamente.

Este repositório público é consultado pelo tablet sem token de acesso. Não coloque credenciais, chaves privadas ou dados pessoais nos arquivos.