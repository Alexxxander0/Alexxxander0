#!/bin/bash
# First, fix sumo and koth editor_give_success issues
sed -i 's/editor_give_success: "{prefix}  editor_give_success:#4CFF7A  editor_give_success:lУспех   editor_give_success:8•   editor_give_success:#E6E6E6Получихте редактор за регион: &f{region}" "{sumo_prefix}&#4CFF7A&lУспех &8• &#E6E6E6Получихте редактор за регион: &f{region}"/editor_give_success: "{sumo_prefix}&#4CFF7A\&lУспех \&8• &#E6E6E6Получихте редактор за регион: \&f{region}"/' DeltaEvents/src/main/resources/lang.yml

sed -i 's/editor_give_success: "{prefix}  editor_give_success:#4CFF7A  editor_give_success:lУспех   editor_give_success:8•   editor_give_success:#E6E6E6Получихте редактор за регион: &f{region}" "{koth_prefix}&#4CFF7A&lУспех &8• &#E6E6E6Получихте редактор за регион: &f{region}"/editor_give_success: "{koth_prefix}&#4CFF7A\&lУспех \&8• &#E6E6E6Получихте редактор за регион: \&f{region}"/' DeltaEvents/src/main/resources/lang.yml

# Then, fix mrbeast and fivem editor_give_success issues which have nested format but the bad string in front
sed -i '/editor_give_success: "{prefix}  editor_give_success:#4CFF7A  editor_give_success:lУспех   editor_give_success:8•   editor_give_success:#E6E6E6Получихте редактор за регион: &f{region}"/d' DeltaEvents/src/main/resources/lang.yml

# Rename /sumo getitems to /sumo giveitems in lang
sed -i 's/usage_getitems: " &8• &f\/sumo getitems/usage_giveitems: " \&8• \&f\/sumo giveitems/g' DeltaEvents/src/main/resources/lang.yml
