# bluelogue - IBM Watson Dialogue Engine

Set up a demo voice-based dialogue app in a sec (uses IBM Watson Cloud)

If you want to impress people in a hackathon with a machine that you can talk to, use bluelog.

# Get a free IBM bluemix account, with both Speech to text and text to speech services.
# Set the secret keys for SST and TTS services in the folowing env Variables:
.. - BLUEMIX_SPEACH_TO_TEXT_UN
.. - BLUEMIX_SPEACH_TO_TEXT_PW
.. - BLUEMIX_TEXT_TO_SPEACH_UN
.. - BLUEMIX_TEXT_TO_SPEACH_PW
# Build bluelog
# Type your expected dialogue with the following format and press Start.

```
<Keyword>:Response
...
```

Once your machine hears the keyword from the dictionary you provided, it will play the response.

Fun example:

hello: Hi! How are you doing?
kitchen: Ok, it's is right ther on the right. Feel free to pick something from the fridge
john: He is fine? What about you? Is all the family doing well?


Now you can be creative and make a dialogue that contains hello, kitchen, and john.
