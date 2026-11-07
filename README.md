# Todo List — Clojure & ClojureScript Full-Stack

**Integrantes da Dupla:**
- **Keven Gomes**
- **Leonardo Ferreira**

🎥 **Vídeo de Apresentação e Demonstração:** [Link do Vídeo no YouTube / Google Drive](https://youtu.be/SEU_LINK_AQUI) *(10 a 15 minutos)*

📖 **Tutorial de Referência:** [Tutorial Clojure/ClojureScript: Construindo uma Aplicação Persistente e Reativa](https://lambdageo-edu.github.io/lambdageo-tutorials/clojure/)

---

## 📌 Descrição do Projeto

Aplicação web full-stack de lista de tarefas (**Todo List**) desenvolvida para a Unidade 3 da disciplina de **Programação Funcional**. O projeto foi construído incrementalmente para vivenciar a arquitetura de sistemas funcionais modernos e reativos:

- **Backend (API REST):** Desenvolvido em **Clojure** sobre a JVM, utilizando **Ring** com o servidor **Jetty** e **Reitit** para roteamento de endpoints com middlewares de serialização JSON (`ring-json`) e controle de acesso seguro (**CORS** com suporte a requisições preflight `OPTIONS`).
- **Banco de Dados (Persistência Real):** **SQLite** em disco (`prod.db`), acessado através da biblioteca funcional **next.jdbc** com queries SQL totalmente parametrizadas contra injeção e uso da cláusula moderna `RETURNING *`.
- **Frontend (Single Page Application):** Desenvolvido em **ClojureScript** compilado via **Shadow-CLJS**, empregando **Reagent 2.0** (sobre **React 19**) com notação Hiccup para reatividade de estado (`r/atom`) e **core.async** (`go`, `<p!`) para chamadas HTTP assíncronas via `fetch`.
- **CRUD Completo:** Suporte a criação de tarefas, listagem em tempo real, alternância de status de conclusão (**Toggle**) e exclusão de itens (**Delete**), com persistência garantida após reinicialização do backend.

---

## 💻 Pré-requisitos e Instalação do Ambiente

Para executar este projeto, seu ambiente precisa contar com **Java JDK**, **Clojure CLI** e **Node.js**.

| Ferramenta | Versão Recomendada | Finalidade |
|---|---|---|
| **Java (JDK)** | 17 ou 21 LTS (mínimo 11) | Runtime da JVM para o Clojure e servidor Jetty |
| **Clojure CLI (`clj` / `clojure`)** | 1.11+ | Resolução de dependências (`deps.edn`) e execução do backend |
| **Node.js & npm** | Node 18+ LTS / npm 9+ | Execução do compilador ClojureScript via Shadow-CLJS |
| **rlwrap** | Qualquer versão recente | Suporte a edição de linha de comando para o comando `clj` |
| **Git** | 2.x | Controle de versão incremental |

### Passo a Passo de Instalação (Linux / Ubuntu / Debian / WSL)

Se estiver configurando uma máquina do zero, execute:

```bash
# 1. Instalar Java JDK 17, curl, git e rlwrap:
sudo apt update
sudo apt install -y openjdk-17-jdk curl git rlwrap

# 2. Instalar a ferramenta oficial Clojure CLI:
curl -O https://download.clojure.org/install/linux-install-1.11.1.1435.sh
chmod +x linux-install-1.11.1.1435.sh
sudo ./linux-install-1.11.1.1435.sh
rm linux-install-1.11.1.1435.sh

# 3. Validar se todas as ferramentas estão disponíveis no PATH:
java -version      # Deve indicar openjdk 17+
clj --version      # Deve indicar Clojure CLI 1.11+
node -v            # Deve indicar v18+ (ex: v20 ou v24)
npm -v
```

> **Dica sobre `clj` vs `clojure`:** O comando `clj` utiliza a biblioteca `rlwrap` para fornecer navegação com setas no terminal. Se em algum sistema operacional o `clj` emitir advertências de terminal, execute simplesmente `clojure -M:run` no lugar de `clj -M:run`.

---

## 🚀 Como Executar o Projeto Localmente

Para rodar a aplicação completa, você precisará de **dois terminais abertos simultaneamente**:

### 1. Clonar o Repositório e Instalar Dependências do Frontend
```bash
git clone https://github.com/KevenGustavo/closure-todo-list.git
cd closure-todo-list
npm install
```

---

### 2. Terminal 1 — Iniciar o Servidor Backend (Porta 3000)
No primeiro terminal, dentro da pasta do projeto, execute:
```bash
clj -M:run
```
*(Caso a ferramenta `rlwrap` não esteja instalada no sistema operacional, utilize: `clojure -M:run`)*

> **Comportamento esperado:** Na primeira execução, o Clojure baixará automaticamente as dependências Maven do `deps.edn`, inicializará a tabela `todos` no banco `prod.db` (se não existir) e exibirá:  
> `Servidor iniciado na porta 3000`

---

### 3. Terminal 2 — Iniciar o Servidor Frontend (Porta 8000)
No segundo terminal, dentro da pasta do projeto, execute:
```bash
npx shadow-cljs watch app
```

> **Comportamento esperado:** O compilador Shadow-CLJS compilará o código ClojureScript para JavaScript, servirá a aplicação e entrará em modo de observação (*hot-reload*), exibindo:  
> `[:app] Build completed.`

---

### 4. Acessar a Aplicação no Navegador
Abra o navegador no endereço:  
👉 **[http://localhost:8000](http://localhost:8000)**

---

## 🔌 Endpoints da API REST (Porta 3000)

| Método | Rota HTTP | Descrição | Exemplo de Payload / Resposta |
|---|---|---|---|
| `GET` | `/api/hello` | Endpoint de verificação de integridade da API | `"Hello, World!"` |
| `GET` | `/api/todos` | Retorna a lista de todas as tarefas cadastradas | `{"todos": [{"todos/id": 1, "todos/title": "...", "todos/completed": 0}]}` |
| `POST` | `/api/todos` | Cria uma nova tarefa no banco de dados | `{"title": "Nova Tarefa"}` $\rightarrow$ Retorna o objeto inserido (status 201) |
| `POST` | `/api/todos/:id/toggle` | Alterna o status `completed` (0 $\leftrightarrow$ 1) | Retorna o registro atualizado (status 200) |
| `DELETE` | `/api/todos/:id` | Remove definitivamente uma tarefa pelo seu ID | Retorna o registro excluído (status 200) |

---

## 🧪 Verificação da Persistência de Dados (Prova de Fogo)

Para validar a persistência real no SQLite exigida pelo edital:
1. Abra `http://localhost:8000`, insira duas tarefas e marque uma delas como concluída.
2. Vá até o **Terminal 1** e encerre o backend pressionando `Ctrl + C`.
3. Inicie o backend novamente com `clj -M:run`.
4. Volte ao navegador e recarregue a página (`F5`).
5. As tarefas e seu estado de conclusão permanecerão intactos, recuperados diretamente do arquivo `prod.db`.

---

## 👥 Histórico de Commits e Contribuição da Dupla

O repositório foi construído de forma estritamente incremental seguindo a especificação *Conventional Commits*, registrando a participação colaborativa de ambos os integrantes:
- **Leonardo Ferreira:** Implementação dos handlers HTTP, roteador Reitit, gerenciamento de estado em memória e persistência SQL com `next.jdbc`.
- **Keven Gomes:** Setup do ambiente ClojureScript/Shadow-CLJS, componentes reativos Reagent, conexão assíncrona via `core.async`, resolução de CORS e estilização CSS.

Consulte o histórico a qualquer momento com:
```bash
git log --oneline
```
