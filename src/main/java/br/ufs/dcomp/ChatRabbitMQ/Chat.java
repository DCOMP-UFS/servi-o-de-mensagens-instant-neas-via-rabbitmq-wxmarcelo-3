package br.ufs.dcomp.ChatRabbitMQ;

import com.rabbitmq.client.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.io.FileOutputStream;
import java.io.File;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;

import java.util.Date;
import java.util.List;
import java.text.SimpleDateFormat;

import com.google.protobuf.util.JsonFormat;
import com.google.protobuf.ByteString;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public class Chat {
  
  private static final String HOST = "AMQP-05c5052587d63958.elb.us-east-1.amazonaws.com";
  private static final String USER = "admin";
  private static final String PASSWORD = "password";
  
  private static final String HOST_API = "http://interface-rabbitmq-14ddf23a3cbeaadd.elb.us-east-1.amazonaws.com";
  private static final String VHOST = "%2F"; // "%2F" representa "/" no URL encoding

  private static Channel channel;
  private static Channel channel_files;
  
  private static String currentUser;
  private static String currentRecipient = "";
  private static String currentGroup = "";
  private static String currentMode = "";
  
  

  public static void main(String[] argv) throws Exception {
    ConnectionFactory factory = new ConnectionFactory();
    factory.setHost(HOST);
    factory.setUsername(USER);
    factory.setPassword(PASSWORD);
    factory.setVirtualHost("/");
    Connection connection = factory.newConnection();
    channel = connection.createChannel();
    channel_files = connection.createChannel();
    
    // Definir usuário atual
    BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
    System.out.print("User: ");
    currentUser = reader.readLine();
    
    // Criar fila para receber mensagens
    channel.queueDeclare(currentUser, false, false, false, null);
    
    // Criar fila para receber arquivos
    channel_files.queueDeclare(currentUser + "_files", false, false, false, null);
    
    // Thread para ouvir mensagens recebidas
    Thread msgReceiverThread = new Thread(() -> {
        
        try {
            
            DeliverCallback deliverCallback = (consumerTag, delivery) -> {
                try {
                    byte[] receivedBytes = delivery.getBody();
                    MensagemProto.Mensagem mensagem = MensagemProto.Mensagem.parseFrom(receivedBytes);
            
                    String dataHora = "(" + mensagem.getData() + " às " + mensagem.getHora() + ") ";
                    String emissor = mensagem.getEmissor();
                    String grupo = mensagem.getGrupo();
                    String conteudoTexto = mensagem.getConteudo().getCorpo().toStringUtf8();
            
                    if(!grupo.isEmpty()){
                        System.out.println("\n" + dataHora + emissor + "#" + grupo + " diz: " + conteudoTexto);
                    }
                    else{
                        System.out.println("\n" + dataHora + emissor + " diz: " + conteudoTexto);
                    }
                    
                    if (currentMode.equals("group")){
                        System.out.print(currentGroup + ">> ");
                    }
                    else {
                        System.out.print(currentRecipient + ">> ");
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            };
            
            channel.basicConsume(currentUser, true, deliverCallback, consumerTag -> {});
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    });
    msgReceiverThread.start();
    
    // Thread para ouvir arquivos recebidas
    Thread fileReceiverThread = new Thread(() -> {
        
        try {
            
            DeliverCallback deliverCallback = (consumerTag, delivery) -> {
                try {
                    byte[] receivedBytes = delivery.getBody();
                    MensagemProto.Mensagem mensagem = MensagemProto.Mensagem.parseFrom(receivedBytes);
            
                    String dataHora = "(" + mensagem.getData() + " às " + mensagem.getHora() + ") ";
                    String emissor = mensagem.getEmissor();
                    String grupo = mensagem.getGrupo();
                    
                    String fileName = mensagem.getConteudo().getNome();
                    byte[] fileBytes = mensagem.getConteudo().getCorpo().toByteArray();
                    String dir = "/home/" + System.getProperty("user.name") + "/chat/" + currentUser + "/downloads/" + grupo + "/" + emissor + "/";
                    new File(dir).mkdirs();
                    FileOutputStream fos = new FileOutputStream(dir + fileName);
                    fos.write(fileBytes);
                    fos.close();
                    
                    String grupo_p =  (!grupo.isEmpty() ? "#" + grupo : "");
                    System.out.println("\n" + dataHora + " Arquivo \"" + fileName + "\" recebido de @" + emissor + grupo_p + "!");
                    
                    if (currentMode.equals("group")){
                        System.out.print(currentGroup + ">> ");
                    }
                    else {
                        System.out.print(currentRecipient + ">> ");
                    }
                    
                } catch (Exception e) {
                    e.printStackTrace();
                }
            };
            
            channel_files.basicConsume(currentUser + "_files", true, deliverCallback, consumerTag -> {});
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    });
    fileReceiverThread.start();
    
    while (true) {
        
    if (currentMode.equals("group")){
        System.out.print((currentGroup.isEmpty() ? "" : currentGroup) + ">> ");
    }
    else {
        System.out.print((currentRecipient.isEmpty() ? "" : currentRecipient) + ">> ");
    }
    
    String input = reader.readLine();

    // Alterar destinatário
    if (input.startsWith("@")) {
        currentRecipient = input;
        currentMode = "individual";
        continue;
    }
    if (input.startsWith("#")){
        currentGroup = input;
        currentMode = "group";
        continue;
    }
     
    if (input.startsWith("!")){
        String[] parts = input.split(" ");
        String command = parts[0];
        
        switch (command) {
            case "!addGroup":
                if (parts.length == 2) {
                    String groupName = parts[1];
                    // Declara um exchange do tipo "fanout" para broadcast das mensagens
                    channel.exchangeDeclare(groupName, "fanout");
                    channel_files.exchangeDeclare(groupName + "_files", "fanout"); // para arquivos
                
                    // Vincula a fila do usuário atual ao exchange do grupo
                    channel.queueBind(currentUser, groupName, "");
                    channel_files.queueBind(currentUser + "_files", groupName + "_files", ""); // para arquivos
                    System.out.println("Grupo '" + groupName + "' criado e vinculado ao usuário " + currentUser);
                } else {
                    System.out.println("Uso correto: !addGroup <nome_grupo>");
                }
                break;
    
            case "!addUser":
                if (parts.length == 3) {
                    String groupName = parts[2];
                    String userName = parts[1];
                    channel.queueBind(userName, groupName, "");
                    channel_files.queueBind(userName + "_files", groupName + "_files", ""); // para arquivos
                    System.out.println("Usuário '" + userName + "' adicionado ao grupo '" + groupName + "'");
                } else {
                    System.out.println("Uso correto: !addUser <nome_usuario> <nome_grupo>");
                }
                break;
    
            case "!delFromGroup":
                if (parts.length == 3) {
                    String groupName = parts[2];
                    String userName = parts[1];
                    channel.queueUnbind(userName, groupName, "");
                    channel_files.queueUnbind(userName + "_files", groupName + "_files", ""); // para arquivos
                    System.out.println("Usuário '" + userName + "' removido do grupo '" + groupName + "'");
                } else {
                    System.out.println("Uso correto: !delFromGroup <nome_usuario> <nome_grupo>");
                }
                break;
    
            case "!removeGroup":
                if (parts.length == 2) {
                    String groupName = parts[1];
                    channel.exchangeDelete(groupName);
                    channel_files.exchangeDelete(groupName + "_files"); // para arquivos
                    System.out.println("Grupo '" + groupName + "' removido.");
                    if (currentGroup.replace("#", "").trim().equals(groupName)){
                        currentMode = "";
                        currentGroup = "";
                        currentRecipient = "";
                    }
                } else {
                    System.out.println("Uso correto: !removeGroup <nome_grupo>");
                }
                break;
                
            case "!upload":
                if (parts.length == 2) {
                    String filePath = parts[1];
                    File file = new File(filePath);
                    String dest = (currentMode.equals("group") ? currentGroup : currentRecipient);
                    if (file.exists() && file.isFile()) {
                        System.out.println("Enviando \"" + filePath + "\" para " + dest + ".");
                        new Thread(() -> sendFile(filePath, dest)).start();
                    } else {
                        System.out.println("Arquivo não encontrado: " + filePath);
                    }
                } else {
                    System.out.println("Uso correto: !upload <caminho_arquivo>");
                }
                break;
                
            case "!listUsers":
                if (parts.length == 2) {
                    String groupName = parts[1];
                    String response = getQueuesBoundToExchange(groupName);
                    System.out.println(response);
                } else {
                    System.out.println("Uso correto: !listUsers <nome_grupo>");
                }
                break;
            
            case "!listGroups":
                if (parts.length == 1) {
                    String response = getExchangesBoundToQueue(currentUser);
                    System.out.println(response);
                } else {
                    System.out.println("Uso correto: !listGroups");
                }
                break;

            default:
                System.out.println("Comando desconhecido.");
        }
        continue;
    }
      
    // Enviar mensagem
    if ((!currentRecipient.isEmpty() && currentMode.equals("individual")) || (!currentGroup.isEmpty() && currentMode.equals("group"))) {
        
        String recipient = currentRecipient.replace("@", "").trim();
        String group = currentGroup.replace("#", "").trim();
        
        MensagemProto.Mensagem mensagem = MensagemProto.Mensagem.newBuilder()
            .setEmissor(currentUser)
            .setData(new SimpleDateFormat("dd/MM/yyyy").format(new Date()))
            .setHora(new SimpleDateFormat("HH:mm").format(new Date()))
            .setGrupo(currentMode.equals("group") ? group : "")
            .setConteudo(MensagemProto.Conteudo.newBuilder()
                .setTipo("text/plain")
                .setCorpo(com.google.protobuf.ByteString.copyFromUtf8(input))
                .build())
            .build();
        
        // Converter para bytes e enviar
        byte[] messageBytes = mensagem.toByteArray();
        
        if (currentMode.equals("group")) {
            channel.basicPublish(group, "", null, messageBytes);
        } else {
            channel.basicPublish("", recipient, null, messageBytes);
        }
        }
    else {
        System.out.println("Selecione um destinatário com @usuario ou #grupo antes de enviar uma mensagem.");
      }
    }
    
  }
  
    private static void sendFile(String filePath, String dest) {
        try {
            
            File file = new File(filePath);
            String fileName = file.getName();
            
            // Detectar o tipo MIME
            Path source = Paths.get(filePath);
            String mimeType = Files.probeContentType(source);
            if (mimeType == null) mimeType = "application/octet-stream";
    
            byte[] fileBytes = Files.readAllBytes(source);
            if (fileBytes == null || fileBytes.length == 0) {
                throw new IOException("Erro ao ler o arquivo: Nenhum dado foi lido.");
            }
            
            ByteString corpoBytes = ByteString.copyFrom(fileBytes);
            
            String grupo_m = currentMode.equals("group") ? currentGroup.replace("#", "") : "";
            
            // Criar mensagem com arquivo usando Protobuf
            MensagemProto.Mensagem mensagem = MensagemProto.Mensagem.newBuilder()
                .setEmissor(currentUser)
                .setData(new SimpleDateFormat("dd/MM/yyyy").format(new Date()))
                .setHora(new SimpleDateFormat("HH:mm").format(new Date()))
                .setGrupo(grupo_m)
                .setConteudo(MensagemProto.Conteudo.newBuilder()
                        .setTipo(mimeType)
                        .setCorpo(corpoBytes)
                        .setNome(fileName)
                        .build())
                .build();
    
            byte[] messageBytes = mensagem.toByteArray();
            
            // Enviar o arquivo para o grupo ou usuário
            if (currentMode.equals("group")) {
                channel_files.basicPublish(currentGroup.replace("#", "")  + "_files", "", null, messageBytes); // para arquivos
            } else {
                channel_files.basicPublish("", currentRecipient.replace("@", "")  + "_files", null, messageBytes);
            }
    
            System.out.println("\nArquivo \"" + fileName + "\" foi enviado para " + dest + "!");
            
            if (currentMode.equals("group")){
                System.out.print(currentGroup + ">> ");
            }
            else {
                System.out.print(currentRecipient + ">> ");
            }
            
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static String getQueuesBoundToExchange(String exchangeName) {
        try {
            // Criar autenticação básica em Base64
            String auth = USER + ":" + PASSWORD;
            String authHeaderValue = "Basic " + Base64.getEncoder().encodeToString(auth.getBytes());
            // Criar cliente REST
            Client client = ClientBuilder.newClient();
            WebTarget target = client.target(HOST_API)
                .path("/api/exchanges/" + VHOST + "/" + exchangeName + "/bindings/source");
            

            // Fazer requisição GET
            Response resposta = target.request(MediaType.APPLICATION_JSON)
                .header("Authorization", authHeaderValue)
                .get();
            
            // Verificar status da resposta
            if (resposta.getStatus() != 200) {
                return "Erro: " + resposta.getStatus();
            }

            // Processar JSON de resposta
            String jsonResponse = resposta.readEntity(String.class);
            return extractQueueDestinations(jsonResponse);
            
        } catch (Exception e) {
            return "Erro ao conectar: " + e.getMessage();
        }
    }
    
    private static String extractQueueDestinations(String jsonResponse) throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode rootNode = objectMapper.readTree(jsonResponse);
        List<String> queueNames = new ArrayList<>();
    
        // Percorrer a resposta JSON
        for (JsonNode node : rootNode) {
            if (node.has("destination_type") && "queue".equals(node.get("destination_type").asText())) {
                queueNames.add(node.get("destination").asText());
            }
        }
    
        // Retornar os nomes das queues separados por ","
        return String.join(", ", queueNames);
    }
    
        public static String getExchangesBoundToQueue(String queueName) {
        try {
            // Criar autenticação básica em Base64
            String auth = USER + ":" + PASSWORD;
            String authHeaderValue = "Basic " + Base64.getEncoder().encodeToString(auth.getBytes());

            // Criar cliente REST
            Client client = ClientBuilder.newClient();
            WebTarget target = client.target(HOST_API)
                .path("/api/queues/" + VHOST + "/" + queueName + "/bindings");

            // Fazer requisição GET
            Response resposta = target.request(MediaType.APPLICATION_JSON)
                .header("Authorization", authHeaderValue)
                .get();

            // Verificar status da resposta
            if (resposta.getStatus() != 200) {
                return "Erro: " + resposta.getStatus();
            }

            // Processar JSON de resposta
            String jsonResponse = resposta.readEntity(String.class);
            return extractExchangeBindings(jsonResponse);

        } catch (Exception e) {
            return "Erro ao conectar: " + e.getMessage();
        }
    }

    private static String extractExchangeBindings(String jsonResponse) throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode rootNode = objectMapper.readTree(jsonResponse);
        List<String> exchangeNames = new ArrayList<>();

        // Percorrer a resposta JSON
        for (JsonNode node : rootNode) {
            if (node.has("source") && !node.get("source").asText().isEmpty()) {
                exchangeNames.add(node.get("source").asText());
            }
        }

        // Retornar os nomes das exchanges separados por ","
        return String.join(", ", exchangeNames);
    }


}
