package br.ufs.dcomp.ChatRabbitMQ;

import com.rabbitmq.client.*;

import java.io.FileOutputStream;
import java.io.File;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;

import java.util.Date;
import java.util.List;
import java.text.SimpleDateFormat;

import com.google.protobuf.util.JsonFormat;

public class Chat {
  
  private static final String HOST = "54.175.105.99";
  private static final String USER = "admin";
  private static final String PASSWORD = "password";
  
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
    Channel channel = connection.createChannel();
    
    // Definir usuário atual
    BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
    System.out.print("User: ");
    currentUser = reader.readLine();
    
    // Criar fila para receber mensagens
    channel.queueDeclare(currentUser, false, false, false, null);
    
    // Thread para ouvir mensagens recebidas
    Thread receiverThread = new Thread(() -> {
        
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
    receiverThread.start();
    
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
                
                    // Vincula a fila do usuário atual ao exchange do grupo
                    channel.queueBind(currentUser, groupName, "");
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
                    System.out.println("Usuário '" + userName + "' removido do grupo '" + groupName + "'");
                } else {
                    System.out.println("Uso correto: !delFromGroup <nome_usuario> <nome_grupo>");
                }
                break;
    
            case "!removeGroup":
                if (parts.length == 2) {
                    String groupName = parts[1];
                    channel.exchangeDelete(groupName);
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
        System.out.println("Selecione um destinatário com @usuario antes de enviar uma mensagem.");
      }
    }
    
  }

}