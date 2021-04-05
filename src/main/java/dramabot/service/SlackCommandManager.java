package dramabot.service;

import com.slack.api.app_backend.slash_commands.payload.SlashCommandPayload;
import com.slack.api.bolt.handler.builtin.SlashCommandHandler;
import com.slack.api.methods.AsyncMethodsClient;
import com.slack.api.methods.SlackApiException;
import com.slack.api.methods.request.chat.ChatPostMessageRequest;
import dramabot.service.model.CatalogEntryBean;
import dramabot.slack.SlackApp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;


@Service
public class SlackCommandManager {

    private static final Logger logger = LoggerFactory.getLogger(SlackCommandManager.class);

    @Autowired
    private CatalogManager catalogManager;

    @Autowired
    public ExecutorService executorService;

    @Value(value = "${slack.botToken}")
    private String botToken;

    public SlashCommandHandler dramabotCommandHandler() {
        return (req, ctx) -> {
            AsyncMethodsClient client = ctx.asyncClient();
            executorService.execute(() -> createAsyncDramabotResponse(req, client));
            return ctx.ack();
        };
    }

    private void createAsyncDramabotResponse(com.slack.api.bolt.request.builtin.SlashCommandRequest req, AsyncMethodsClient client) {
        Map<String, List<CatalogEntryBean>> authors = new HashMap<>();
        Map<String, List<CatalogEntryBean>> allBeans = SlackManagerUtils.fillAuthorsAndReturnAllBeansWithDatabaseContent(authors, catalogManager);
        SlashCommandPayload payload = req.getPayload();
        String userId = payload.getUserId();
        String userName = payload.getUserName();
        String channelId = payload.getChannelId();
        String channelName = payload.getChannelName();
        String command = payload.getCommand();
        String payloadText = payload.getText();
/*
        String responseUrl = payload.getResponseUrl();
*/

        StringBuilder resultBuilder = new StringBuilder();

        // default response in channel
        String responseType = SlackApp.IN_CHANNEL;

        logger.debug("In channel {} '{}' " + "was sent by {}. The text was '{}', with UserId: {} ChannelId:{}",
                channelName, command, userName, payloadText, userId, channelId);
        if (!payloadText.contains("catalogo")) {
            SlackManagerUtils.appendPayload(authors, allBeans, payloadText,
                    resultBuilder, responseType);
            String text = resultBuilder.toString();
            logger.debug("answer is: {}", text);
            String iconEmoji = payloadText.contains(" amo") ? ":heart:" : null;
            ChatPostMessageRequest asyncRequest = ChatPostMessageRequest.builder().text(text).channel(channelId)
                    .iconEmoji(iconEmoji).token(botToken).build();
            client.chatPostMessage(asyncRequest);
        } else {
            try {
                SlackManagerUtils.doCatalogCsvResponse(client, userId, channelId, botToken);
            } catch (IOException | SlackApiException e) {
                logger.debug("Error in /dramabot - command while searching for catalog", e);
            }
        }
    }

}
