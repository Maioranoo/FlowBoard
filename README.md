# FlowBoard

Sistema Full Stack para gerenciamento inteligente de tarefas, desenvolvido como desafio técnico para a XCL.

O FlowBoard permite criar, editar, excluir e acompanhar tarefas de forma simples, além de utilizar Inteligência Artificial para gerar sugestões de produtividade.

---

## Projeto Online

**Frontend:** [https://SEU-LINK-VERCEL.vercel.app](https://flow-board-lovat.vercel.app/)

**Backend:** [https://SEU-LINK-RENDER.onrender.com/tasks](https://flowboard-backend-qnut.onrender.com/tasks)](https://flowboard-backend-qnut.onrender.com/tasks)

---

## Funcionalidades

- ✅ Cadastro de tarefas
- ✅ Edição de tarefas
- ✅ Exclusão personalizada com confirmação
- ✅ Alteração de status
- ✅ Filtros por status
- ✅ Dashboard com estatísticas
- ✅ Indicador de produtividade
- ✅ Sugestões inteligentes utilizando IA
- ✅ Persistência dos dados em banco PostgreSQL
- ✅ Interface responsiva
- ✅ Feedback visual (Toast)
- ✅ Ícones utilizando Lucide Icons

---

## Tecnologias Utilizadas

### Frontend

- HTML5
- CSS3
- JavaScript
- Lucide Icons

### Backend

- Java 17
- Spring Boot
- Spring Data JPA
- Hibernate
- Maven

### Banco de Dados

- PostgreSQL
- Neon Database

### Deploy

- Vercel (Frontend)
- Render (Backend)

---

## Estrutura do Projeto

```
FlowBoard
│
├── backend
│   ├── controller
│   ├── model
│   ├── repository
│   ├── service
│   ├── config
│   └── resources
│
└── front-end
    ├── css
    ├── js
    └── index.html
```

---

## Como executar localmente

### Backend

```bash
cd backend

./mvnw spring-boot:run
```

O backend será iniciado em:

```
http://localhost:8080
```

---

### Frontend

Abra o arquivo

```
front-end/index.html
```

ou utilize a extensão Live Server do VS Code.

---

## Banco de Dados

O sistema utiliza PostgreSQL.

Para execução local basta configurar o arquivo:

```
application.properties
```

com as credenciais do banco.

---
## Objetivo

O FlowBoard foi desenvolvido como solução para gerenciamento de tarefas, buscando oferecer uma experiência simples, intuitiva e moderna, utilizando uma arquitetura Full Stack baseada em Java Spring Boot no backend e JavaScript puro no frontend.

---

## Desenvolvedor

João Pedro Maiorano

GitHub:
https://github.com/Maioranoo
