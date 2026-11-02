# Arquitetura e Separação de Plataformas — Squi TV

Este documento descreve a divisão de responsabilidades entre o aplicativo Android (`:app`), o núcleo compartilhado (`:shared`) e o futuro aplicativo Desktop Windows (`:desktopApp`).

## 1. Módulos do Repositório

* **`:app`**: Aplicação Android nativa. Contém UI em Jetpack Compose, ExoPlayer (Media3), Room Database, WorkManager, serviços em foreground, integrações com Android TV/Mobile e adaptadores de atualização específicos do Android.
* **`:shared`**: Núcleo puro em Kotlin/JVM independente de Android. Contém utilitários de versão, contratos de manifesto de atualização e validação de artefatos. Não possui dependências de Android (`android.*`, `Context`, etc.).
* **`:desktopApp`** *(Planejado)*: Futura aplicação Compose Multiplatform para Windows Desktop. Consumirá `:shared` para regras de negócio portáveis e implementará seus próprios adaptadores de player e sistema de arquivos.

## 2. Invariantes Arquiteturais

1. **`shared` nunca depende de aplicações**: O fluxo de dependência é estritamente unidirecional (`app -> shared`, `desktopApp -> shared`).
2. **Separação de Plataforma**: Players de vídeo, armazenamento local seguro, notificações e permissões de sistema são responsabilidade exclusiva de cada plataforma.
3. **Contrato de Atualização Único**: A lógica de parsing de versões, validação de manifesto e seleção de artefato é centralizada em `:shared` para garantir comportamento idêntico no Android e no Windows.

## 3. Metadados do aplicativo instalado e testes de atualização

`UpdateModule` fornece `InstalledAppInfo` ao `UpdateRepository` por Hilt. Em produção, os dados vêm de `AppVersionProvider`, `BuildConfig`, pacote e capacidades reais do Android. Os testes fornecem uma fixture release explícita, sem modificar o pacote ou `BuildConfig` do projeto.

`force` ignora somente o cache/intervalo de consulta; não permite que uma variante debug consulte atualizações release. A proteção de debug é verificada antes da consulta de rede, inclusive quando `force=true`.

Para artefatos Android, `shared` exige interseção entre as ABIs reais do APK e as informadas pelo aparelho. Ser um dispositivo de 64 bits não comprova suporte a aplicativos de 32 bits nem compatibilidade entre ARM e x86. `universal` requer lista explícita; `all` é inválido. `any` se aplica somente a APKs sem bibliotecas nativas.

## 4. Distribuição compartilhada

O cliente de atualização tem qualificador próprio `UpdateOkHttpClient` e política HTTPS própria, sem reescrita de URLs pelo provedor IPTV. `UpdateUrlPolicy` valida a associação inicial entre repositório, tag e asset. Testes usam certificado/origem HTTPS locais; produção não permite HTTP.

Modelos e validação são compartilhados; transporte, versão instalada, assinatura e instalação permanecem Android. O gerador extrai os dados do APK real e a tarefa `:shared:validateUpdateManifest` valida a saída com o mesmo contrato usado no app. O futuro Windows deve consumir esse contrato, sem reutilizar instalação de APK ou depender de código Android.

Consultar `contrato-atualizacoes.md` e `checklist-release.md` antes de mudar schema, versões ou publicação. Clientes legados ainda dependem do APK na release latest; publicar Windows exclusivo sem respeitar a transição rompe esse caminho.
