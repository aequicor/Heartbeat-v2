# Diagram engines

The application serves these original release assets from its private renderer origin.
Building or running the application does not require npm or a diagram service.

| Source release | Files |
| --- | --- |
| [Mermaid 11.16.1](https://registry.npmjs.org/mermaid/-/mermaid-11.16.1.tgz) | `package/dist/mermaid.min.js`, `package/LICENSE` |
| [PlantUML 1.2026.10 MIT](https://registry.npmjs.org/@plantuml/core/-/core-1.2026.10.tgz) | `plantuml.js`, `viz-global.js`, `themes.js`, `openiconic.js`, `emoji.js`, `LICENSE` |
| [PlantUML browser release 1.2026.10](https://github.com/plantuml/plantuml/releases/download/v1.2026.10/js-plantuml-1.2026.10.zip) | `c4.min.js` with the [C4-PlantUML license](https://github.com/plantuml-stdlib/C4-PlantUML/blob/release/v2.14.0/LICENSE) |

Copy only the listed assets when updating the engines. The renderer loads the engines on
first use. C4 is the bundled external PlantUML standard-library collection.

SHA-256 for the supplied engine assets follows.

```text
18327bef70d96fb505fe7287d9f6a7362ebf07ff6576ddfaffb1a06f3e1a2954  mermaid.min.js
c6664799aaa464319b5e1070a69850b93a7ea23ee41a23e920a8f2086e7da21c  plantuml.js
fc6ca2de83dd4e3fca967d5214122777890a5cd1ab101115a1f716fc84e6dc5e  viz-global.js
6b6d2ef1590a29dfc24c1462b56dfd3b31757724da3627af5ea15dc1e991bb6d  themes.js
b34a6d7c0315ad4c1d780bcfe92e7bfa89abbd4fa20ae419e3409f1cc4f56c52  openiconic.js
e3b97a103b48bb00a6d77cf0667f3a0fda0a3799cfbc5e22494fcde024a18e49  emoji.js
455ba941efb5b1bed8de33dd2f55db53c7be0de7f86aabe04bcbc3d1024051e7  c4.min.js
```
