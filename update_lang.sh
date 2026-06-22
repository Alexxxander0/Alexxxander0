#!/bin/bash
# Adding color usage strings to lang.yml
cat << 'EOT' >> DeltaEvents/src/main/resources/lang.yml

color:
  usage_header: "&8&m----------------------------------------"
  usage_title: "&#4AA3FF&lColor Event Команди:"
  usage_mrbeast_start: " &8• &f/color mrbeast start &7- Стартира MrBeast"
  usage_mrbeast_stop: " &8• &f/color mrbeast stop &7- Спира MrBeast"
  usage_mrbeast_editor: " &8• &f/color mrbeast editor spawn &7- Редактор на спаун"
  usage_mrbeast_reload: " &8• &f/color mrbeast reload &7- Релоудва MrBeast"
  usage_fivem_start: " &8• &f/color fivem start &7- Стартира FiveM"
  usage_fivem_stop: " &8• &f/color fivem stop &7- Спира FiveM"
  usage_fivem_editor: " &8• &f/color fivem editor [arena|spawn] &7- Редактор за FiveM"
  usage_fivem_reload: " &8• &f/color fivem reload &7- Релоудва FiveM"
  usage_footer: "&8&m----------------------------------------"
EOT

# Ensure missing translations exist
sed -i 's/  editor_give_success:/  editor_give_success: "{prefix}&#4CFF7A&lУспех &8• &#E6E6E6Получихте редактор за регион: \&f{region}"/g' DeltaEvents/src/main/resources/lang.yml
sed -i '/mrbeast:/a \  only_players: "&#FF4C4C&lГрешка &8• &#E6E6E6Тази команда може да се използва само от играчи!"' DeltaEvents/src/main/resources/lang.yml
sed -i '/fivem:/a \  only_players: "&#FF4C4C&lГрешка &8• &#E6E6E6Тази команда може да се използва само от играчи!"' DeltaEvents/src/main/resources/lang.yml
