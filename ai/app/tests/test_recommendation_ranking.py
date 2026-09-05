from app.tools.item_search import ItemRow, rank_items


def item(item_id: int, category: str, campus: str, favorites: int = 0) -> ItemRow:
    return ItemRow(item_id, f"商品 {item_id}", "", 1000, None, "GOOD", campus, "", category, 0, favorites, "")


def test_ranking_uses_real_favorite_and_preference_features():
    selected = rank_items(
        [item(1, "数码", "东校区", 4), item(2, "教材", "校本部", 0)],
        budget=1500, campus="", preferred_categories={"教材"}, preferred_campuses={"校本部"},
    )

    assert selected[0].item_id == 2
